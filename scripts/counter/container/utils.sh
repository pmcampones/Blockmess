#!/bin/bash

source scripts/counter/utils.sh
source scripts/docker_init.sh

function launchReplica() {
	eval "docker container run -d --name replica$REPLICA_IDX --net blockmess_network --cap-add=NET_ADMIN blockmess_csd $FILE_LOC $CHANGE $OPS_PER_REPLICA address=replica$REPLICA_IDX contact=replica0:$CONTACT_PORT"
}
