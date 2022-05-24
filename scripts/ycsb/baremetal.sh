#!/bin/bash

source scripts/ycsb/utils.sh

eval "java -cp target/BlockmessLib.jar $CALL_ARGS"
