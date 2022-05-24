#!/bin/bash

source scripts/register/utils.sh
source scripts/docker_init.sh

function launchReplica() {
	IP=$(( REPLICA_IDX + 10 ))
	eval "docker container run -d --name replica$REPLICA_IDX --ip 192.168.0.$IP --net blockmess_network --cap-add=NET_ADMIN blockmess_csd $FILE_LOC $OPERATION_TYPE $CHANGE $OPS_PER_REPLICA address=192.168.0.$IP contact=192.168.0.10:$CONTACT_PORT"
}
