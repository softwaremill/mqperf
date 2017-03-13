## Prerequisites
In order to use this playbook you'll need:
- ansible and boto installed on your computer
- an AWS account

## Configuration
Just ensure that following environment variables are set: 
`AWS_SECRET_ACCESS_KEY`, `AWS_ACCESS_KEY_ID`

Change S3 bucket name in `ansible/group_vars/all.yml`. Bucket name needs to be globally unique, so that's why we need it.

## Provision necessary EC2 instances, security groups, VPC and mongo replica set
`ansible-playbook create_dynamodb_table.yml`
`ansible-playbook install_and_setup_mongo.yml`
`ansible-playbook provision_mqperf_nodes.yml`

Or just

`ansible-playbook create_all.yml`

## Run mongo tests
`ansible-playbook run-mongo-tests.yml`

## Shutting down EC2 instances, deleting DB and S3 and clean-up
`ansible-playbook delete_dynamodb_table.yml`
`ansible-playbook delete_s3_bucket.yml`
`ansible-playbook shutdown_ec2_instances.yml`

Or just

`ansible-playbook delete_all.yml`


