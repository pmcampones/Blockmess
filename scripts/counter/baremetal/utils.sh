#!/bin/bash

source scripts/counter/utils.sh

function launchReplica() {
	PORT=$(( CONTACT_PORT + REPLICA_IDX ))
	eval "java -cp target/BlockmessLib.jar $FILE_LOC $CHANGE $OPS_PER_REPLICA port=$PORT redirectFile=./outputLogs/node$REPLICA_IDX.txt 2>&1 | sed 's/^/[replica$REPLICA_IDX] /' &"
}
