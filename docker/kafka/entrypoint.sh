#!/bin/bash

### see https://github.com/wurstmeister/kafka-docker/issues/389
### 20 sec sleep gives chance to Zookeeper to cleanup previous sessions

sleep 20
echo "Starting Confluent Kafka"
/etc/confluent/docker/run
