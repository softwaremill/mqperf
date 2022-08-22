#!/bin/bash

# Functions
check_configuration_for_aws() {
    if [ -z $TF_VAR_BUCKET_NAME ] && [ -z $TF_VAR_AWS_REGION ]; then
        echo "Environment variables: TF_VAR_BUCKET_NAME and TF_VAR_AWS_REGION need to be set before running this script"
        exit 1
    fi

    if [ -z $TF_VAR_BUCKET_NAME ]; then
        echo "Environment variable: TF_VAR_BUCKET_NAME needs to be set before running this script"
        exit 1
    fi

    if [ -z $TF_VAR_AWS_REGION ]; then
        echo "Environment variable: TF_VAR_AWS_REGION needs to be set before running this script"
        exit 1
    fi
}

create_infrustracture() {
    echo "Creating infrastucture on $1 to test $2 messaging queue"
    terragrunt run-all apply --terragrunt-working-dir "$(dirname "$0")"/live --terragrunt-non-interactive
}

# Main
echo "Select cloud provider: "
select cloud_provider in GKE AWS AZURE; do
    case "$cloud_provider" in
    "GKE")
        echo "GKE is not supported yet"
        ;;

    "AWS")
        echo "You selected AWS"
        check_configuration_for_aws
        break
        ;;

    "AZURE")
        echo "AZURE is not supported yet"
        ;;

    *) echo "Wrong selection, select again: " ;;
    esac
done

echo "Select mq to test: "
select mq_to_test in KAFKA; do
    case "$mq_to_test" in
    "KAFKA")
        echo "You selected KAFKA"
        break
        ;;
    *) echo "Wrong selection, select again: " ;;
    esac
done

create_infrustracture $cloud_provider $mq_to_test
