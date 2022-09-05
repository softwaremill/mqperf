#!/bin/bash

## In case of unhandled error
set -e

file=$1
green='\033[0;32m'
clear='\033[0m'

# Functions
is_configfile_parameter_null() {
    if [ -z "$1" ]; then
        check=false
        while [ $check == false ]; do
            echo "Configfile not provided. Continue with interactive mode? [y/n]"
            read -r selection
            if [ "$selection" == "y" ]; then
                true
                check=true
            elif [ "$selection" == "n" ]; then
                exit
            fi
        done
    else
        false
    fi
}

get_user_input() {
    CHECK=false
    while [ $CHECK == false ];
    do
        printf "\nSelect cloud provider: \n1 - AWS\n2 - GCP\n3 - Azure\n\n"
        read -r CLOUD_PROVIDER
        if [ "$CLOUD_PROVIDER" == 1 ]; then
            CLOUD_PROVIDER="AWS"
            CHECK=true
        elif [ "$CLOUD_PROVIDER" == 2 ]; then
            CLOUD_PROVIDER="GCP"
            CHECK=true
        elif [ "$CLOUD_PROVIDER" == 3 ]; then
            CLOUD_PROVIDER="Azure"
            CHECK=true
        else printf "\nSelect again or ctrl+c for exit\n\n"
        fi
    done    
    export TF_VAR_CLOUDPROVIDER=$CLOUD_PROVIDER

    CHECK=false
    while [ $CHECK == false ];
    do
        printf "\nSelect MQ: \n1 - Kafka\n2 - RabbitMQ\n\n"
        read -r MQ
        if [ "$MQ" == 1 ]; then
            MQ="Kafka"
            CHECK=true
        elif [ "$MQ" == 2 ]; then
            MQ="RabbitMQ"
            CHECK=true
        fi  
    done 
    export TF_VAR_MQ=$MQ

    printf "\nEnter bucket workspace name:\n\n"
    read -r WORKSPACE_NAME
    export TF_VAR_WORKSPACE_NAME=$WORKSPACE_NAME
}

confirm_config() {
    printf "\nSelected configuration:\n"
    printf "${green}Cloud provider: %s\n" "$TF_VAR_CLOUDPROVIDER"
    printf "MQ: %s\n" "$TF_VAR_MQ"
    printf "Bucket name: %s\n" "$TF_VAR_BUCKETNAME"
    printf "Cluster name: %s\n" "$CLUSTER_NAME"
    check=false
    while [ $check == false ]; do
        echo "${clear}Confirm configuration to proceed [y/n]"
        read -r selection
        if [ "$selection" == "y" ]; then
            true
            check=true
        elif [ "$selection" == "n" ]; then
            exit
        fi
    done
}

check_configfile_exists() {
    if test -f "$file"; then
        echo "Config file exists."
        true
    else
        echo "Config file not found."
        false
    fi
}

import_config_from_file() {
    echo "--> Importing configuration from file to ENVS.\n"
    input="$file"
    while IFS= read -r line
    do
        key=${line%% *}
        value=${line#* }
        export $key=$value
        echo "Imported: ${green}$(env | grep -w "$key")${clear}"
    done < "$input"
}

export_config_to_file() {
    true
    # TO DO
}

create_infrustracture() {
    check=false
    while [ $check == false ]; do
        echo "${clear}\nCreate infrastructure? [y/n]"
        read -r selection
        if [ "$selection" == "y" ]; then
            true
            check=true
        elif [ "$selection" == "n" ]; then
            exit
        fi
    done
    echo "Creating infrastucture..."
    terragrunt run-all init --terragrunt-working-dir "$(dirname "$0")"/live/$TF_VAR_MQ/$TF_VAR_CLOUDPROVIDER --terragrunt-non-interactive
    terragrunt run-all apply --terragrunt-working-dir "$(dirname "$0")"/live/$TF_VAR_MQ/$TF_VAR_CLOUDPROVIDER --terragrunt-non-interactive
}

select_bucket() {
    case $TF_VAR_CLOUDPROVIDER in
        AWS)
        TF_VAR_BUCKETNAME="S3_bucket"
        ;;
        GCP)
        TF_VAR_BUCKETNAME="GCS_bucket"
        ;;
        Azure)
        TF_VAR_BUCKETNAME="Azure_bucket"
        ;;
    esac
}

# Main

if is_configfile_parameter_null "$@"; then
    get_user_input
    select_bucket
    confirm_config
    export_config_to_file
else
    check_configfile_exists
    import_config_from_file
    create_infrustracture
fi
