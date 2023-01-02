#!/bin/bash


for INSTANCE_ID in $(aws ec2 describe-instances \
    --filters "Name=tag:Name,Values=queues-pool" "Name=tag:eks:cluster-name,Values=$CLUSTER_NAME" "Name=instance-state-name,Values=running" \
    --query "Reservations[*].Instances[*].InstanceId" --output text )
do
    device_name=$(aws ec2 describe-instances \
    --instance-ids $INSTANCE_ID \
    --query "Reservations[*].Instances[*].BlockDeviceMappings[*].DeviceName" --output text |  awk '{ print $2 }')

    jq -n --arg var "$device_name" '[{"DeviceName":$var,"Ebs":{"DeleteOnTermination":true}}]' > mapping.json

    aws ec2 modify-instance-attribute --instance-id $INSTANCE_ID --block-device-mappings file://mapping.json 

done
