from curses.ascii import isdigit
from re import T, X
import subprocess
from jsonpath_ng import jsonpath, parse
from genericpath import exists
import sys
import json
import os


def is_configfile_parameter_null():
    if len(sys.argv) != 3:
        print("You need to provide terragrunt action and config file name as parameter. Exiting script.")
        exit()


def check_action():
    action = sys.argv[1]
    if action not in ["plan", "apply", "destroy", "destroy-cluster"]:
        print('Provided action "' +
              sys.argv[1] + '" does not exist. \nAvailable actions: plan, apply, destroy, destroy-cluster. Exiting script.')
        exit()


def check_configfile_exists():
    file_exists = exists(sys.argv[2])
    if not file_exists:
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
        os.environ["APP_IMAGE"] = parse_json_file("instance.app_image")
        os.environ["APP_NODES_NUMBER"] = parse_json_file("instance.app_nodes_number")
        os.environ["APP_NODES_TYPE"] = parse_json_file("instance.app_nodes_type")
        os.environ["CONTROLLER_NODES_NUMBER"] = parse_json_file("instance.controller_nodes_number")
        os.environ["CONTROLLER_NODES_TYPE"] = parse_json_file("instance.controller_nodes_type")
        os.environ["QUEUE_NODES_NUMBER"] = parse_json_file("instance.queue_nodes_number")
        os.environ["QUEUE_NODES_TYPE"] = parse_json_file("instance.queue_nodes_type")


def set_kubernetes_provider():
    global kubernetes_provider
    match os.getenv("TF_VAR_CLOUDPROVIDER"):
        case "aws":
            kubernetes_provider = str("eks")
        case "gcp":
            kubernetes_provider = str("gke")
        case "az":
            kubernetes_provider = str("aks")
    os.environ["TF_VAR_KUBERNETESPROVIDER"] = kubernetes_provider    


def select_workspace():
    set_kubernetes_provider()        
    output = str(subprocess.check_output(f"cd live/$TF_VAR_MQ/$TF_VAR_CLOUDPROVIDER/{kubernetes_provider} ; terragrunt workspace list", shell=True))
    workspaces = output.replace("b'","").replace("'","").replace("\\n","").replace("*","").split()
    
    print("Select cluster to DELETE:")
    check=False
    while check == False:
        print("0 - exit")
        count=0
        for i in workspaces:
            count+=1
            print(str(count) + " - " + i)

        selected_workspace = input()
        if isdigit(selected_workspace) and int(selected_workspace) in range(1, count+1):
            print("Selected cluster: " + workspaces[int(selected_workspace) - 1])
            check = True
        elif int(selected_workspace) == 0:
            print("Exiting script.")
            exit()
        else:
            print("Select again proper value:")
    
    os.environ["CLUSTER_NAME"] = workspaces[int(selected_workspace) - 1]


def run_terragrunt():
    path = os.path.dirname(os.path.abspath(__file__))
    bash_command_init = f"terragrunt run-all init --terragrunt-working-dir {path}/live/$TF_VAR_MQ/$TF_VAR_CLOUDPROVIDER --terragrunt-non-interactive"
    bash_command_plan_or_apply = f"terragrunt run-all {sys.argv[1]} --terragrunt-working-dir {path}/live/$TF_VAR_MQ/$TF_VAR_CLOUDPROVIDER --terragrunt-non-interactive"
    bash_command_destroy = f"terragrunt run-all {sys.argv[1]} --terragrunt-working-dir {path}/live/$TF_VAR_MQ/$TF_VAR_CLOUDPROVIDER --terragrunt-non-interactive"
    bash_command_workspace_swith_default_kubernetes_provider = f"terragrunt workspace select default --terragrunt-working-dir {path}/live/$TF_VAR_MQ/$TF_VAR_CLOUDPROVIDER/$TF_VAR_KUBERNETESPROVIDER --terragrunt-non-interactive"
    bash_command_workspace_delete_kubernetes_provider = f"terragrunt workspace delete $CLUSTER_NAME  --terragrunt-working-dir {path}/live/$TF_VAR_MQ/$TF_VAR_CLOUDPROVIDER/$TF_VAR_KUBERNETESPROVIDER --terragrunt-non-interactive"
    bash_command_workspace_swith_default_mq = f"terragrunt workspace select default --terragrunt-working-dir {path}/live/$TF_VAR_MQ/$TF_VAR_CLOUDPROVIDER/$TF_VAR_MQ --terragrunt-non-interactive"
    bash_command_workspace_delete_mq = f"terragrunt workspace delete $CLUSTER_NAME  --terragrunt-working-dir {path}/live/$TF_VAR_MQ/$TF_VAR_CLOUDPROVIDER/$TF_VAR_MQ --terragrunt-non-interactive"
    bash_command_workspace_swith_default_prometheus = f"terragrunt workspace select default --terragrunt-working-dir {path}/live/$TF_VAR_MQ/$TF_VAR_CLOUDPROVIDER/prometheus --terragrunt-non-interactive"
    bash_command_workspace_delete_prometheus = f"terragrunt workspace delete $CLUSTER_NAME  --terragrunt-working-dir {path}/live/$TF_VAR_MQ/$TF_VAR_CLOUDPROVIDER/prometheus --terragrunt-non-interactive"

    match sys.argv[1]:
        case "plan":
            subprocess.run(bash_command_init, shell=True, check=True)
            subprocess.run(bash_command_plan_or_apply, shell=True, check=True)
        case "apply":
            subprocess.run(bash_command_init, shell=True, check=True)
            subprocess.run(bash_command_plan_or_apply, shell=True, check=True)
        case "destroy":
            set_kubernetes_provider()
            subprocess.run(bash_command_destroy, shell=True, check=True)
            subprocess.run(bash_command_workspace_swith_default_kubernetes_provider, shell=True, check=True)
            subprocess.run(bash_command_workspace_delete_kubernetes_provider, shell=True, check=True)
            subprocess.run(bash_command_workspace_swith_default_mq, shell=True, check=True)
            subprocess.run(bash_command_workspace_delete_mq, shell=True, check=True)
            subprocess.run(bash_command_workspace_swith_default_prometheus, shell=True, check=True)
            subprocess.run(bash_command_workspace_delete_prometheus, shell=True, check=True)
        case "destroy-cluster":
            select_workspace()            
            subprocess.run(bash_command_destroy, shell=True, check=True)
            subprocess.run(bash_command_workspace_swith_default_kubernetes_provider, shell=True, check=True)
            subprocess.run(bash_command_workspace_delete_kubernetes_provider, shell=True, check=True)
            subprocess.run(bash_command_workspace_swith_default_mq, shell=True, check=True)
            subprocess.run(bash_command_workspace_delete_mq, shell=True, check=True)
            subprocess.run(bash_command_workspace_swith_default_prometheus, shell=True, check=True)
            subprocess.run(bash_command_workspace_delete_prometheus, shell=True, check=True)


def terragrunt_infrastructure():
    set_envs()
    try:
        run_terragrunt()
    except subprocess.CalledProcessError:
        exit(1)


def main():
    check_action()
    is_configfile_parameter_null()
    check_configfile_exists()
    terragrunt_infrastructure()


main()
