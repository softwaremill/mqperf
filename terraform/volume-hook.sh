#!/bin/bash


for INSTANCE_ID in $(aws ec2 describe-volumes \
    --filters "Name=tag:ebs.csi.aws.com/cluster,Values=true" "Name=tag:KubernetesCluster,Values=$CLUSTER_NAME" \
    --query "Volumes[*].Attachments[*].InstanceId" --output text )
do
    device_name=$(aws ec2 describe-volumes --filters "Name=attachment.instance-id,Values=$INSTANCE_ID" \
    --query "Volumes[*].Attachments[*].Device" --output text |  tail -n 1)

    jq -n --arg var "$device_name" '[{"DeviceName":$var,"Ebs":{"DeleteOnTermination":true}}]' > mapping.json

    aws ec2 modify-instance-attribute --instance-id $INSTANCE_ID --block-device-mappings file://mapping.json 

done
