#!/bin/bash

function runProgram() {
	CONTACT_PORT=6000
	for REPLICA_IDX in $(seq 0 $(( NUM_REPLICAS - 1)) )
	do
		PORT=$(( CONTACT_PORT + REPLICA_IDX ))
		CHANGE=$(( REPLICA_IDX + 1 ))
		eval "java -cp target/BlockmessLib.jar $FILE_LOC $CHANGE $OPS_PER_REPLICA port=$PORT redirectFile=./outputLogs/node$REPLICA_IDX.txt 2>&1 | sed 's/^/[replica$REPLICA_IDX] /' &"
		echo "Replica $REPLICA_IDX running $FILE_LOC on port $PORT and executing $OPS_PER_REPLICA operations, each adding $CHANGE to the shared counter"
	done
}
