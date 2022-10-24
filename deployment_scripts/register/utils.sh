#!/bin/bash

source scripts/init_verifier.sh

function runProgram() {
  CONTACT_PORT=6000
  for REPLICA_IDX in $(seq 0 $(( NUM_REPLICAS - 1)) )
  do
    CHANGE=$(( REPLICA_IDX + 1 ))
    OPERATION_TYPE=$(( REPLICA_IDX % 2 ))
    launchReplica
    echo "Replica $REPLICA_IDX running $FILE_LOC executing $OPS_PER_REPLICA operations, consisting of operation $OPERATION_TYPE with value $CHANGE over the shared register"
  done
}
