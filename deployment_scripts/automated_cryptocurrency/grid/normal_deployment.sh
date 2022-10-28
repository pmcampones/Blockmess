#!/bin/bash

NUM_NODES=$1
INTERVAL=$2
EXECUTION_TIME=$3
OUTPUT_DIRECTORY=$4

if [ -z $NUM_NODES ] || [ -z $INTERVAL ] || [ -z $EXECUTION_TIME ] || [ -z $OUTPUT_DIRECTORY ];
then
  echo "$0 <Number_Nodes_in_System> <Proposal_Interval> <Execution_Time> <Output_Directory_Pathname>"
	echo "Number Nodes in System:     Number of nodes to be instantiated in the Blockmess system execution."
	echo "Proposal Interval:          The average time (in miliseconds) between tx proposals for any node."
	echo "Execution Time:             Elapsed time of the execution of the Blockmess system."
	echo "Output Directory Pathname:  Pathname of the directory where the node logs will be saved."
  exit
fi

source deployment_scripts/automated_cryptocurrency/grid/grid_common.sh

function runBlockmess() {
  SLEEP_TIME=1
	CONVERT_TO_MILISECONDS=1000 #miliseconds/second
	LAST_NODE_OFFSET=1000 #miliseconds
	for NODE_IDX in $(seq 0 $NUM_NODES)
	do
		HOST=${HOST_ARRAY[ (( $NODE_IDX % $NUM_HOSTS )) ]}
		NODES_AHEAD=$(( $NUM_NODES - $NODE_IDX ))
		#INITIALIZATION_TIME=$(( $NODES_AHEAD * $SLEEP_TIME * $CONVERT_TO_MILISECONDS + $LAST_NODE_OFFSET ))
		INITIALIZATION_TIME=$(( NODES_AHEAD * CONVERT_TO_MILISECONDS * SLEEP_TIME + LAST_NODE_OFFSET ))
		echo "Starting to run blockmess node $NODE_IDX on host $HOST. Starting to 'mine' after $INITIALIZATION_TIME"
		oarsh $HOST "docker container run -d --name node_$NODE_IDX --net blockmess_network --cap-add=NET_ADMIN blockmess-tc bash -c 'tc qdisc add dev eth0 root netem delay 75ms rate 31mbit && java -cp target/BlockmessLib.jar demo.cryptocurrency.client.AutomatedClient $INTERVAL keys/pub_keys_repo.txt address=node_$NODE_IDX contact=node_0:6000 myPublic=./keys/public_$(( REPLICA_IDX + 1 )).pem mySecret=./keys/secret_$(( REPLICA_IDX + 1 )).pem interface=eth0 expectedNumNodes=$NUM_NODES initializationTime=$INITIALIZATION_TIME'"
		sleep 1
	done
	sleep $EXECUTION_TIME
}

function extractResults() {
  mkdir outputLogs/$OUTPUT_DIRECTORY
  for NODE_IDX in $(seq 0 $NUM_NODES)
  do
    HOST=${HOST_ARRAY[ (( $NODE_IDX % $NUM_HOSTS )) ]}
    oarsh $HOST "docker container exec node_$NODE_IDX cat outputLogs/changesChains.csv > outputLogs/$OUTPUT_DIRECTORY/changesChains_node$NODE_IDX.csv"
    oarsh $HOST "docker container exec node_$NODE_IDX cat outputLogs/discardedBlocks.csv > outputLogs/$OUTPUT_DIRECTORY/discardedBlocks_node$NODE_IDX.csv"
    oarsh $HOST "docker container exec node_$NODE_IDX cat outputLogs/finalizedBlocks.csv > outputLogs/$OUTPUT_DIRECTORY/finalizedBlocks_node$NODE_IDX.csv"
    oarsh $HOST "docker container exec node_$NODE_IDX cat outputLogs/finalizedTransactions.csv > outputLogs/$OUTPUT_DIRECTORY/finalizedTransactions_node$NODE_IDX.csv"
    oarsh $HOST "docker container exec node_$NODE_IDX cat outputLogs/transactionProposals.csv > outputLogs/$OUTPUT_DIRECTORY/transactionProposals_node$NODE_IDX.csv"
    oarsh $HOST "docker container exec node_$NODE_IDX cat outputLogs/unfinalizedBlocks.csv > outputLogs/$OUTPUT_DIRECTORY/unfinalizedBlocks_node$NODE_IDX.csv"
    oarsh $HOST "docker container logs node_$NODE_IDX > outputLogs/$OUTPUT_DIRECTORY/logs_node$NODE_IDX.log"
  done
}

executeCommon
runBlockmess
extractResults
deleteContainers
