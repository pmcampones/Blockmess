#!/bin/bash

NUM_NODES=$1
INTERVAL=$2

HOSTS=$(oarprint host)
NUM_HOSTS=$(oarprint host | wc -l)

function installDocker() {
	for HOST in $HOSTS
	do
		echo "Installing docker in host $HOST"
		oarsh $HOST "sudo-g5k /grid5000/code/bin/g5k-setup-docker" &
	done
	wait
}

function createSwarm() {
  for HOST in $HOSTS
  	do
  		#Login is necessary to mitigate the number of pull requests allowed by docker hub
  		echo "Logging into docker account in host $HOST"
  		oarsh $HOST "docker login" &
  	done
  	wait

  	docker swarm init
  	JOIN_TOKEN=$(docker swarm join-token manager -q)
  	MASTER=$(hostname)
  	echo "Initialized docker swarm on leader $MASTER"
  	for HOST in $(oarprint host);
  	do
    		if [ $HOST != $MASTER ];
  		then
  			echo "Joining swarm with host $HOST"
     	 		oarsh $HOST "docker swarm join --token $JOIN_TOKEN $MASTER:2377"
    		fi
  	done
  	wait
}


function createNetwork() {
	echo "Creating (or attempting to) network blockmess_network"
	#docker network rm blockmess_network
	docker network create --attachable --driver overlay blockmess_network
}

function genKeys() {
  echo "Generating keys"
  ./keys/genManyKeys.sh 0 $NUM_NODES
  > keys/pub_keys_repo.txt
  for IDX in $(seq 0 $NUM_NODES)
  do
    echo keys/public_$IDX.pem >> keys/pub_keys_repo.txt
  done
}

function genBootstrapDB() {
    rm -r DB
    java -cp target/BlockmessLib.jar demo.cryptocurrency.utxos.UTXOGenerator $NUM_NODES keys/pub_keys_repo.txt
}

function buildDockerImage(){
  for HOST in $HOSTS
  do
    echo "Building docker image in node $HOST"
    oarsh $HOST "docker build -t blockmess-tc -f Dockerfile_Traffic_Control ." &
  done
  wait
}

function hostsIntoArray() {
	HOST_ARRAY=()
	for HOST in $HOSTS
	do
		HOST_ARRAY+=( $HOST )
	done
}

function runBlockmess() {
  SLEEP_TIME=1
	CONVERT_TO_MILISECONDS=1000 #miliseconds/second
	LAST_NODE_OFFSET=20000 #miliseconds
	for NODE_IDX in $(seq 0 $NUM_NODES)
	do
		HOST=${HOST_ARRAY[ (( $NODE_IDX % $NUM_HOSTS )) ]}
		#NODES_AHEAD=$(( $NUM_NODES - $NODE_IDX ))
		#INITIALIZATION_TIME=$(( $NODES_AHEAD * $SLEEP_TIME * $CONVERT_TO_MILISECONDS + $LAST_NODE_OFFSET ))
		echo "Starting to run blockmess node $NODE_IDX on host $HOST. Starting to 'mine' after $INITIALIZATION_TIME"
		oarsh $HOST "docker container run -d --name node_$NODE_IDX --net blockmess_network --cap-add=NET_ADMIN blockmess-tc bash -c 'tc qdisc add dev eth0 root netem delay 75ms && java -cp target/BlockmessLib.jar demo.cryptocurrency.client.AutomatedClient $INTERVAL keys/pub_keys_repo.txt address=node_$NODE_IDX contact=node_0:6000 myPublic=./keys/public_$(( REPLICA_IDX + 1 )).pem mySecret=./keys/secret_$(( REPLICA_IDX + 1 )).pem interface=eth0'"
		sleep 1
	done
	EXECUTION_TIME=600
	sleep $EXECUTION_TIME
}

function stopContainers() {
    for HOST in $HOSTS
    do
      oarsh $HOST 'docker container stop $(docker container ls -aq)'
    done
}

function extractResults() {
  for NODE_IDX in $(seq 0 $NUM_NODES)
  do
    HOST=${HOST_ARRAY[ (( $NODE_IDX % $NUM_HOSTS )) ]}
    oarsh $HOST "docker container exec node_$NODE_IDX cat outputLogs/changesChains.csv > outputLogs/changesChains_node$NODE_IDX.csv"
    oarsh $HOST "docker container exec node_$NODE_IDX cat outputLogs/discardedBlocks.csv > outputLogs/discardedBlocks_node$NODE_IDX.csv"
    oarsh $HOST "docker container exec node_$NODE_IDX cat outputLogs/finalizedBlocks.csv > outputLogs/finalizedBlocks_node$NODE_IDX.csv"
    oarsh $HOST "docker container exec node_$NODE_IDX cat outputLogs/finalizedTransactions.csv > outputLogs/finalizedTransactions_node$NODE_IDX.csv"
    oarsh $HOST "docker container exec node_$NODE_IDX cat outputLogs/transactionProposals.csv > outputLogs/transactionProposals_node$NODE_IDX.csv"
    oarsh $HOST "docker container exec node_$NODE_IDX cat outputLogs/unfinalizedBlocks.csv > outputLogs/unfinalizedBlocks_node$NODE_IDX.csv"
  done
}

function deleteContainers() {
  for HOST in $HOSTS
  do
    oarsh $HOST "docker container rm $(docke  r container ls -aq)"
  done
}

installDocker
createSwarm
createNetwork

##Single machine
genKeys
genBootstrapDB
################

buildDockerImage
hostsIntoArray
runBlockmess
stopContainers
extractResults
deleteContainers
