#!/bin/bash

source scripts/register/baremetal/utils.sh

NUM_REPLICAS=$1
OPS_PER_REPLICA=$2
FILE_LOC="demo.register.AsyncRegister"

verifyInitialization $0
runProgram
