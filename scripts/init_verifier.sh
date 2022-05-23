#!/bin/bash

function verifyInitialization() {
	if [ -z $NUM_REPLICAS ] || [ -z $OPS_PER_REPLICA ];
	then
		echo "$1 <Number of replicas> <Number of operations per replica>"
		echo "Number of replicas: Dictates how may replicas will be used in this execution."
		echo "Number of operations per replica: How many operations each replica will execute." 
		exit
	fi
}
