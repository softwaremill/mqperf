from re import T
from jsonpath_ng import jsonpath, parse
from genericpath import exists
import sys
import json
import os


def is_configfile_parameter_null():
    if len(sys.argv) == 3:
        return True
    else:
        print("You need to provide terragrunt action and config file name as parameter. Exiting script.")
        exit()

def check_action():
    action = sys.argv[1]
    if action == "plan" or action == "apply" or action == "destroy":
        True
    else:
        print('Provided action "' +
              sys.argv[1] + '" does not exist. \nAvailable actions: plan, apply, destroy. Exiting script.')
        exit()


def check_configfile_exists():
    file_exists = exists(sys.argv[2])
    if file_exists:
        True
    else:
        print('Provided configfile "' +
              sys.argv[2] + '" does not exist. Exiting script.')
        exit()
        


def parse_json_file(json_path):
    with open(sys.argv[2], 'r') as json_file:
        json_data = json.load(json_file)
    jsonpath_expression = parse(json_path)
    for match in jsonpath_expression.find(json_data):
        return(match.value)


def set_envs():
        os.environ["TF_VAR_CLOUDPROVIDER"] = parse_json_file("instance.cloudprovider")
        os.environ["TF_VAR_BUCKET_NAME"] = parse_json_file("instance.bucket_name")
        os.environ["TF_VAR_MQ"] = parse_json_file("instance.mq")
        os.environ["CLUSTER_NAME"] = parse_json_file("instance.cluster_name")
        os.environ["NODES_NUMBER"] = parse_json_file("instance.nodes_number")


def get_envs():
    global cloud_provider
    global mq
    global bucket_name
    global cluster_name
    global nodes_number
    cloud_provider = os.getenv("TF_VAR_CLOUDPROVIDER")
    mq = os.getenv("TF_VAR_MQ")
    bucket_name = os.getenv("TF_VAR_BUCKET_NAME")
    cluster_name = os.getenv("CLUSTER_NAME")
    nodes_number = os.getenv("NODES_NUMBER")
    print(cluster_name)

def confirm_config():
    True


def run_terragrunt():
    bash_command_init = "terragrunt run-all init --terragrunt-working-dir ""$(dirname ""$0"")""/live/$TF_VAR_MQ/$TF_VAR_CLOUDPROVIDER --terragrunt-non-interactive"
    bash_command_apply = "terragrunt run-all "+sys.argv[1]+" --terragrunt-working-dir ""$(dirname ""$0"")""/live/$TF_VAR_MQ/$TF_VAR_CLOUDPROVIDER --terragrunt-non-interactive"
    if sys.argv[1] == "plan" or sys.argv[1] == "apply":
        os.system(bash_command_init)

    os.system(bash_command_apply)


def terragrunt_infrastructure():
    set_envs()
    run_terragrunt()

def main():
    check_action()
    is_configfile_parameter_null()
    check_configfile_exists()
    set_envs()
    terragrunt_infrastructure()

main()
