#!/bin/bash

NUM_REPLICAS=$1
INTERVAL=$2
KEYS=$3

source scripts/docker_init.sh


if [ -z $NUM_REPLICAS ] || [ -z $INTERVAL ] || [ -z $KEYS ];
then
  echo "$1 <Number of replicas> <Interval from Tx proposals> <Public Keys Pathname>"
  echo "Number of replicas: Dictates how may replicas will be used in this execution."
  echo "Interval from Tx proposals: Interval of time between transaction proposals for a single node."
  echo "Public Keys Pathname: The pathname of the file containing the pathnames of the public keys of all nodes."
  exit
fi

CONTACT_PORT=6000
for REPLICA_IDX in $(seq 0 $(( NUM_REPLICAS - 1)) )
do
  eval "docker container run -d --name replica$REPLICA_IDX --net blockmess_network --cap-add=NET_ADMIN blockmess_csd demo.cryptocurrency.client.AutomatedClient $INTERVAL $KEYS address=replica$REPLICA_IDX contact=replica0:$CONTACT_PORT myPublic=./keys/public_$(( REPLICA_IDX + 1 )).pem mySecret=./keys/secret_$(( REPLICA_IDX + 1 )).pem interface=eth0"
  echo "Replica $REPLICA_IDX running $FILE_LOC proposing with an interval of $INTERVAL"
  sleep 2
done
