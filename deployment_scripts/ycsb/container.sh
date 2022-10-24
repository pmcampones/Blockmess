#!/bin/bash

source scripts/ycsb/utils.sh
source scripts/docker_init.sh

eval "docker container run -d --name replica blockmess_csd $CALL_ARGS"
