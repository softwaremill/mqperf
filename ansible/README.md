## Prerequisites
In order to use this playbook you'll need:
- ansible and boto installed on your computer
- an AWS account

## Configuration
Just ensure that following environment variables are set: 
`AWS_SECRET_ACCESS_KEY`, `AWS_ACCESS_KEY_ID`

## Provision necessary EC2 instances, security groups, VPC and mongo replica set
`ansible-playbook install_and_setup_mongo.yml`

## Shutting down EC2 instances & security groups clean-up
`ansible-playbook shutdown_ec2_instances.yml`
