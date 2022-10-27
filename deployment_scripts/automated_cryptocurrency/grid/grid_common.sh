#!/bin/bash

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
    java -cp target/BlockmessLib.jar demo.cryptocurrency.utxos.UTXOGenerator 100000 keys/pub_keys_repo.txt
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

function deleteContainers() {
  for HOST in $HOSTS
  do
    oarsh $HOST "docker container ls -aq | xargs docker container rm -f"
  done
}

function executeCommon() {
  installDocker
  createSwarm
  createNetwork
  genKeys
  genBootstrapDB
  buildDockerImage
  hostsIntoArray
}
