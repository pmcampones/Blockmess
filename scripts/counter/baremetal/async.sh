#!/bin/bash

source scripts/counter/baremetal/utils.sh

NUM_REPLICAS=$1
OPS_PER_REPLICA=$2
FILE_LOC="demo.counter.AsyncCounter"

verifyInitialization $0
runProgram
