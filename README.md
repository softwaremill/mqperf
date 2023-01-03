# mqperf v2

## Overview

mqperf is a set of tools and a site for benchmarking message queue performance, and evaluating message queue characteristics.

## Setting up MQ clusters

Here you can find information on how to automatically setup clusters of various MQ servers, using different cloud providers.

### Prerequisites

1. Install [Terraform](https://www.terraform.io/) version v1.2.6 or newer.
2. Install [Terragrunt](https://terragrunt.gruntwork.io/) version 0.38.7 or newer.
3. Install [Python](https://www.python.org/) version v3.10 or newer.
4. Install [jsonpath-ng](https://pypi.org/project/jsonpath-ng/) version v1.5.3 or newer.

#### Configure the cloud provider

##### AWS

1. Create the [AWS IAM](https://docs.aws.amazon.com/cli/latest/userguide/getting-started-prereqs.html) user and install the [AWS CLI](https://docs.aws.amazon.com/cli/latest/userguide/cli-chap-getting-started.html).
2. Configure basic settings for the AWS CLI. Use the `aws configure` CLI command. You will be prompted for configuration values such as your AWS Access Key Id, your AWS Secret Access Key and the deafult AWS Region. See the [Quick setup](https://docs.aws.amazon.com/cli/latest/userguide/getting-started-quickstart.html) documentation for more details.

##### GCP
1. If you're new to Google Cloud, [create an account](https://console.cloud.google.com/freetrial?_ga=2.87728222.758173713.1664359592-186345793.1659692414&_gac=1.194118239.1663587334.CjwKCAjwpqCZBhAbEiwAa7pXeQdSwnWTCp9XrOL6d450eTiDEqiiq133plcZFiDE36QpyKtwubhX4xoCsAYQAvD_BwE) and follow the *Before you begin* steps from the [GKE Quickstart tutorial](https://cloud.google.com/kubernetes-engine/docs/deploy-app-cluster).
2. Download and install the [gcloud CLI](https://cloud.google.com/sdk/gcloud#download_and_install_the).
3. Export the `TF_VAR_GCP_PROJECT` variable, where `"your-project-id"` is the name of your GCP project to manage resources in:
   ```
   export TF_VAR_GCP_PROJECT="your-project-id"
   ``` 

##### Azure
1. Create the [Azure account](https://azure.microsoft.com/en-us/free/) and instal the [Azure CLI](https://learn.microsoft.com/en-us/cli/azure/install-azure-cli).
2. Sign in to the Azure CLI by using the [az login](https://learn.microsoft.com/en-us/cli/azure/reference-index?view=azure-cli-latest#az-login) command. 
3. Make sure the identity you are using to create your cluster has the appropriate minimum permissions. For more details on access and identity for AKS, see [Access and identity options for Azure Kubernetes Service (AKS)](https://learn.microsoft.com/en-us/azure/aks/concepts-identity).

#### Configure the backend
In this repo, we are using [Amazon S3 bucket](https://aws.amazon.com/s3/) to store the Terraform state, so it is required to configure the AWS account. The backend configuration is similiar among all of the cloud providers - the S3 bucket is used also for the GCP or Azure code. To configure your bucket, provide the `backend_name` variable in the JSON config file. See the [Terraform documentation](https://www.terraform.io/language/settings/backends/s3) for more details about the S3 backend.

### Quick start 

Run the script:
```
python3 script.py apply example-data.json
```

### Using python script

`terraform/script.py` provides an automated way of performing terragrunt actions against configuration stored in json file.

#### Command
Run the script with the following command:
```bash
python3 script.py [terragrunt action] [json file]
```
where `[terragrunt action]` is:
```
[terragrunt action]: plan, apply, destroy, destroy-cluster
```

#### Json file

```json
{
    "instance": {
        "cloudprovider": "[cloud_provider]",
        "bucket_name": "[cloud_bucket_name]",
        "mq": "[mq_name]",
        "cluster_name": "[cluster_name]",
        "nodes_number": "[number_of_nodes]",
        "app_image": "[app_image]"
    }
}

```
|Name|Accepted values|
|---|---|
| cloudprovider |"aws" , "gcp" , "az"|
| cloud_bucket_name |"s3-bucket-mqperf" |
| mq_name |"kafka" , "rabbitmq", "postgresql", "mongodb" |
| cluster_name|`string`| 
| number_of_nodes |`string`|
| app_image | `string` |

#### Example command
```
python3 script.py apply example-data.json
```

#### [example-data.json](https://github.com/softwaremill/mqperf/blob/workspace-listing/terraform/example-data.json)

```json
{
    "instance": {
        "cloudprovider": "aws",
        "bucket_name": "s3-bucket-mqperf",
        "mq": "rabbitmq",
        "cluster_name": "rabbit01",
        "nodes_number": "4",
        "app_image": "softwaremill/mqperf-kafka:latest"
    }
}
```

### The file/folder structure

In this repo, Terragrunt is used to keep configurations DRY and to work with multiple Terraform modules. Follow the [Terragrunt](https://terragrunt.gruntwork.io/docs/) documentation to learn more.

In `terraform/live` there are defined Terragrunt files. Each MQ service has its own folder with the configuration specific for each cloud provider. 

In `terraform/live` each folder consists of just one `terragrunt.hcl` file per component. The `terragrunt.hcl` file contains the source URL of the module to deploy and the inputs to set for that module in the current folder. It also contains dependencies and definitions for the providers. 

With the use of Terragrunt, you can define root configuration and then include in each of your child `terragrunt.hcl` files. An example could be the `terraform/live/_envcommon/` folder, which contains the common configuration accros all MQ services. In `_envcommon/eks.hcl` you will find a Terragrunt file with a configuration of the AWS EKS module, that is being used for multiple MQ services. To use this configuration for Kafka, in `kafka/aws/eks/terragrunt.hcl` simply use the `include` block:
```
include "envcommon" {
  path = "${dirname(find_in_parent_folders())}/../../_envcommon/eks.hcl"
}
```


The code in this repo uses the following folder hierarchy:
```
.
└── terraform/
    ├── workspace-hook.sh
    ├── script.py
    ├── live/
    │   ├── _envcommon/
    │   │   ├── eks.hcl
    │   │   ├── gke.
    │   │   ├── aks.hcl
    │   │   ├── storage.hcl
    │   │   └── prometheus.hcl
    │   ├── kafka/
    │   │   ├── _mqcommon/
    │   │   │   └── kafka.hcl
    │   │   ├── aws/
    │   │   │   ├── app/
    │   │   │   │   └── terragrunt.hcl
    │   │   │   ├── eks/
    │   │   │   │   └── terragrunt.hcl
    │   │   │   ├── prometheus/
    │   │   │   │   └── terragrunt.hcl
    │   │   │   ├── kafka/
    │   │   │   │   └── terragrunt.hcl
    │   │   │   ├── storage-class/
    │   │   │   │   └── terragrunt.hcl
    │   │   │   ├── k8s_providers.hcl
    │   │   │   └── terragrunt.hcl
    │   │   └── gcp/
    │   │   └── az/
    │   ├── mongodb/   
    │   ├── postgresql/ 
    │   └── rabbitmq/
    ├── modules/
    │   ├── app/
    │   ├── helm/
    │   ├── kafka/
    │   ├── mongodb/
    │   ├── postgresql/
    │   ├── rabbitmq/
    │   └── storage-class/
    └── tests/
```

Where:
- **terraform/live/**: Main folder containing Terragrunt code for all MQ services.
- **_envcommon/**: Folder contains common configurations across all MQ services, such as [EKS](https://aws.amazon.com/eks/)/[GKE](https://cloud.google.com/kubernetes-engine)/[AKS](https://azure.microsoft.com/en-us/products/kubernetes-service/#overview) clusters configurations. 
- **kafka/**: At the top level are each of MQ services such as Kafka, Rabbitmq. This specific folder contains the configuration for Kafka.
- **_mqcommon/**: Folder contains the common configuration across specific MQ. The common configuration will be applied across every cloud provider for Kafka MQ service. 
- **aws/**: Within each MQ service there will be one or more cloud providers, such as [AWS](https://aws.amazon.com/) or [GCP](https://cloud.google.com/). Within each cloud provider, you deploy all the resources for that cloud provider - for example the [EKS AWS](https://aws.amazon.com/eks/) and [Kubernetes](https://kubernetes.io/pl/) resources. 
- **app/**: Folder contains the MQ client app configuration with the image definition in a terragrunt.hcl file.
- **eks/**: Folder contains the [EKS AWS](https://aws.amazon.com/eks/) service configuration defined in a terragrunt.hcl file.
- **prometheus/**: Folder contains the [kube-prometheus-stack](https://github.com/prometheus-community/helm-charts) Helm chart configuration defined in a terragrunt.hcl file.
- **kafka/**: Folder contains [Strimzi Kafka](https://strimzi.io/) cluster-operator and Kafka cluster Helm charts configuration defined in a terragrunt.hcl file.
- **storage-class/**: Folder contains configuration for the [AWS GP3 Storage Class](https://docs.aws.amazon.com/eks/latest/userguide/ebs-csi.html) configuration.
- **k8s_providers.hcl**: Terragrunt file containing configuration for the [Kubernetes](https://registry.terraform.io/providers/hashicorp/kubernetes/latest/docs) and [Helm](https://registry.terraform.io/providers/hashicorp/helm/latest/docs) providers.
- **terragrunt.hcl**: Terragrunt file containing configuration for the [AWS](https://registry.terraform.io/providers/hashicorp/aws/latest/docs) provider
- **modules/**: Folder contains reusable Terraform modules.

### Adding a new MQ service
You can use the files that are already in the repo as the guidelines and examples for your configuration. The file/folder structure remains the same for each MQ service.

1. Start from creating the new folder `newQueueName` in `terraform/modules/` .
2. Add your Terraform configuration files necessary for your new queue deployment. Those could be Kubernetes or Helm resources. Later, in Terragrunt file, you will source that configuration within a block:
   ```
   terraform {
   source = "../../../../modules//newQueueName"
   }
   ```
3. Create the new folder `newQueueName` in `terraform/live/`.
4. In `terraform/live/newQueueName/` create the folder called `_mqcommon`. Then, create a file called `newQueueName.hcl`. 
5. In this file source your Terraform module defined in `terraform/modules/newQueueName/` with the use of a code block from step 2. The `newQueueName.hcl` configuration will be applied for each cloud provider.
6. The next step is to create folders for each of the cloud providers you want to use. Let's assume for this tutorial you want to use only the AWS cloud provider. 
7. In `terraform/live/newQueueName/` create the folder called `aws`.
8. In `terraform/live/newQueueName/aws/` create the following folders:
   - `app`
   - `eks`
   - `newQueueName`
   - `prometheus`
   - `storage-class`
9. In each of those folders create the `terragrunt.hcl` file. It's a child file that contains the configuration of a parent or other `.hcl` files defined in parent folders with the use of the `include` block. 
    ```
    include "root" {
    path = find_in_parent_folders()
    }

    include "envcommon" {
    path = "${dirname(find_in_parent_folders())}/../../_envcommon/eks.hcl"
    }
    ```

   Additionally, you can define `dependency`, `inputs` block or other Terragrunt blocks to suit your deployment requirements. 

10. In `terraform/live/newQueueName/aws/` create the files:
    - `k8s_providers.hcl`
    - `terragrunt.hcl`
    In `k8s_providers.hcl` file define Kubernetes and Helm providers configuration. 
    In `terragrunt.hcl` file define the cloud provider configuration.  
11. The configuration varies between specific cloud providers. To add the new cloud provider for `newQueueName` MQ service, lookup the `terraform/live/kafka/gcp/` or `terraform/live/kafka/az/` folders and follow their file/folder structure. For the GCP the structure below will be reqiured:
    - `app/`
    - `gke/`
    - `newQueueName/`
    - `prometheus/`
    - `k8s_providers.hcl`
    - `terragrunt.hcl`
   See the examples in the repo for the details of the files/folders.
    

### Kafka KRaft MQ

To use the [Kafka KRaft](https://strimzi.io/docs/operators/in-development/configuring.html#ref-operator-use-kraft-feature-gate-str) feature, override these variables from the `terraform/live/kafka/variables.tf` file with the values specified below:
```yml
variable "chart_version" {
  type        = string
  description = "Define the Strimzi Kafka Operator version"
  default     = "0.29.0"
}

variable "sets" {
  type = list(map(any))
  default = [
    {
      name  = "featureGates"
      value = "+UseKRaft\\,+UseStrimziPodSets"
    }
  ]
}

variable "kafka_kraft_enabled" {
  type        = bool
  description = "Enable Kafka KRaft feature"
  default     = true
}
```

### Storage configuration

You can use the reusable storage-class module defined in `terraform/modules/storage-class/` folder to create Kubernetes Storage Class resource for the [AWS EKS](https://aws.amazon.com/eks/) and necessary [AWS IAM permissions](https://docs.aws.amazon.com/eks/latest/userguide/csi-iam-role.html) for the EBS CSI driver. 

To use this module create folder `storage-class` in `terraform/live/$MQ/$CLOUD_PROVIDER/`, where `$MQ` is your message queue service and `$CLOUD_PROVIDER` is your cloud provider of choice. In folder `terraform/live/$MQ/$CLOUD_PROVIDER/storage-class/` create file `terragrunt.hcl` and provide all necessary code for your terragrunt configuration. Additionally, in the inputs block define:

1. Define variable eks_storage_classes:
```yml
eks_storage_classes = [
    {
      name                      = "mqperf-storageclass"
      storage_class_provisioner = "ebs.csi.aws.com"
      volume_binding_mode       = "WaitForFirstConsumer"
      parameters = {
        type   = "gp3"
        fsType = "ext4"
      }
    }
  ]
```
For the parameters details refer to the [Kubernetes documentation](https://kubernetes.io/docs/concepts/storage/storage-classes/#parameters).

2. Define variables as in the example below:

```
aws_account_id    = get_aws_account_id()
oidc_provider_url = replace(dependency.eks.outputs.eks_cluster_oidc_issuer_url, "https://", "")
cluster_name      = get_env("CLUSTER_NAME")
```

In the `terraform/live/_envcommon/storage.hcl` you can define the values of the global variables: persistent volume capacity and preferred storage class for the [AWS EKS](https://aws.amazon.com/eks/). The [GCP](https://cloud.google.com/kubernetes-engine) configuration supports for now only the standard Storage Class. These variables will be applied to every MQ service. For the values, pass the name of the storage class and the size of the storage expressed as a Kubernetes resource quantity:

```
inputs = {
  storage_class = "mqperf-storageclass"
  storage_size  = "20Gi"
}
```

If you don't define these variables, the default values will be passed:
- The **storage_class** variable has a default value `standard`.
- The **storage_size** variable has a default value `20Gi`.


## MQ clients

The MQ clients implement sender & receiver functionality, to provide the configured node during performance and throughput testing.
For each MQ, a docker image with the clients for that MQ is built. When starting the container, an HTTP server is started. 
The server exposes the following endpoints:

* `GET /metrics`
* `GET /in-progress` - a JSON array of names of processes (either `sender` or `reciever`) that have been started by this server
* `POST /init` - initialize thq MQ (e.g. creating topics), as described by the given configuration; ideally should be run once before starting a sender/receiver
* `POST /start/receiver` and `POST /start/sender` - start a process of receiving/sending, as described by the given configuration

The server by default binds to `0.0.0.0:8080`, but this can be customised by setting the `http.host` and `http.port` environment variables.

The structure of the config JSON is:

```json
{
  "testId": "test",
  "testLengthSeconds": 10,
  "msgsPerProcessInSecond": 10,
  "msgSizeBytes": 100,
  "batchSizeSend": 1,
  "senderConcurrency": 4,
  "batchSizeReceive": 1,
  "receiverConcurrency": 4,
  "mqConfig":{  
    
  }
}
```

See docs in `Config` for an explanation of the parameters.

### Kafka

To test the senders/receivers using Kafka, you might either use the docker image from the [repository](https://hub.docker.com/repository/docker/softwaremill/mqperf-kafka), or build one locally using: 

```
sbt kafka/docker:publishLocal
```

Then, go to `docker/kafka` and run
```
docker-compose -f ../metrics/docker-compose.metrics.yml -f docker-compose.yml -p kafka up
```
> **_NOTE:_** Please remember to start the client file after the metrics file <br>
> -p flag is used in order to give a specified name to the whole container

This will start zookeeper, the kafka broker,
Confluent's control center (available at `http://localhost:9021`), and the mqperf server.

To init, then start the sender/receiver, use the following commands, using appropriate endpoints:

```bash
curl -XPOST -d'{"testId":"test","testLengthSeconds":10,"msgsPerProcessInSecond":10,"msgSizeBytes":100,"batchSizeSend":1,"senderConcurrency":200,"batchSizeReceive":1,"receiverConcurrency":200,"mqConfig":{"hosts":"broker:29092","topic":"mqperf-test","acks":"-1","groupId":"mqperf","commitMs":"1000","partitions":"10","replicationFactor":"1"}}' http://localhost:8080/init
```

## Working with AWS cluster

0. Using `aws configure` set credentials and region (e.g. eu-central-1)

1. Start the cluster according to the chosen config file:
    ```
    cd terraform
    python3 script.py apply tests/config-kafka-aws.json
    ```
   NOTE: mqperf v1 used r5.2xlarge instances

2. link kubectl to the cluster
    ```
    aws eks --region eu-central-1 update-kubeconfig --name kafka
    ```
3. Retrieve broker credentials:
- RabbitMQ
   ```
  kubectl get secret rabbitmq-cluster-default-user -o yaml
  ```
  values are coded. In order to encode:
   ```
  echo -n <coded username/password> | base64 -d
  ```
   NOTE: after encoding the result will have a '%' sign at the end. It should be omitted.
   NOTE 2: hostname: rabbitmq-cluster.default.svc

- Postgress
   ```
  kubectl get secret/mqperfuser.mqperf-postgresql-cluster.credentials.postgresql.acid.zalan.do -o json
  echo -n 'putBase64Here' | base64 -d
  ```
  
4. Port-forwarding:
- grafana (e.g. port 9090 locally) (admin/prom-operator)
    ```   
    kubectl port-forward services/kube-prometheus-stack-grafana 9090:80
    ```
- postgres cluster (e.g. 9543 port locally)
    ```
  kubectl port-forward services/mqperf-postgresql-cluster-0 9543:5432
  ```
  
5. Useful commands:
- display all pods:
   ```
  kubectl get pod
   ```
- display all services:
  ```
    kubectl get services
   ```
- display broker settings (including username and password)
  ```
   kubectl get secret
  ```
- restart app pods
  ```
    kubectl scale deployment app-deployment --replicas 0
    kubectl scale deployment app-deployment --replicas <nr_of_app_nodes>
   ```
  NOTE: it is possible to refresh the cluster in order to use the newest version of the mqperf app docker image.
        Add `imagePullPolicy: Always` to app.yml file
- pulling the newest app image without imagePullPolicy: Always and without a need to restart the whole cluster
    ```
    kubectl get deployment app-deployment -o yaml > temp-deployment.yaml' # get current conf
    # here modify downloaded configuration file `temp-deployment.yaml` as you need
    kubectl delete deployment app-deployment # delete current conf
    kubectl apply -f temp-deployment.yaml # apply `temp-deployment.yaml` as new conf
    ``` 
  And scale the deployment afterwards (restart app pods)
- follow specific app node logs
  Get pod name using `kubectl get pod`
    ```
    kubectl logs <pod_name> --follow
    ```
### FAQ
1. Fedora 37 issues
- python 3.11 not working at the moment - 3.10 works fine
- install additional modules: kubernetes, portforward, wheel, golang

2. After adding a new queue client, mqperf app does not get deployed to cluster
Answer: Remember to add the `terragrunt.hcl` file to `terraform/live/:queueName/aws/app`. Content of the file is common for all queues so you can copy it for example from `terraform/live/kafka/aws/app`.

## Copyright

Copyright (C) 2013-2022 SoftwareMill [https://softwaremill.com](https://softwaremill.com).
