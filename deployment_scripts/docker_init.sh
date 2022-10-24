#!/bin/bash

docker build -t blockmess_csd .
docker network create --driver bridge --subnet 192.168.0.0/24 blockmess_network
