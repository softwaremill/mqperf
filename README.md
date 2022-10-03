# mqperf v2
## Overview

## Prerequisites
1. Install [Terraform](https://www.terraform.io/) version 0.38.7 or newer.
2. Install [Terragrunt](https://terragrunt.gruntwork.io/) version v1.2.6 or newer.
3. Install [Python](https://www.python.org/) version v3.10 or newer.
4. Install [jsonpath-ng](https://pypi.org/project/jsonpath-ng/) version v1.5.3 or newer.
### Configure the cloud provider
#### AWS
1. Create the [AWS IAM](https://docs.aws.amazon.com/cli/latest/userguide/getting-started-prereqs.html) user and install the [AWS CLI](https://docs.aws.amazon.com/cli/latest/userguide/cli-chap-getting-started.html).
2. Configure basic settings for the AWS CLI. Use the `aws configure` CLI command. You will be prompted for configuration values such as your AWS Access Key Id, your AWS Secret Access Key and the deafult AWS Region. See the [Quick setup](https://docs.aws.amazon.com/cli/latest/userguide/getting-started-quickstart.html) documentation for more details.

#### GCP
1.
2.

## Quick start 
Run the script:
```
python3 script.py apply example-data.json
```


## The file/folder structure
In this repo, Terragrunt is used to keep configurations DRY and to work with multiple Terraform modules. Follow the [Terragrunt](https://terragrunt.gruntwork.io/docs/) documentation to learn more.

In `terraform/live` each folder consists of just one terragrunt.hcl file per component. The terragrunt.hcl files contain the source URL of the module to deploy and the inputs to set for that module in the current folder. It also contains dependencies and definitions for the providers.

The code in this repo uses the following folder hierarchy:
```
.
└── terraform/
    ├── workspace-hook.sh
    ├── setup.sh
    ├── live/
    │   ├── _envcommon/
    │   │   ├── eks.hcl
    │   │   ├── gke.hcl
    │   │   └── prometheus.hcl
    │   ├── kafka/
    │   │   ├── _mqcommon/
    │   │   │   └── kafka.hcl
    │   │   ├── aws/
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
    │   ├── mongodb/   
    │   └── rabbitmq/
    ├── modules/
    │   ├── helm/
    │   ├── kafka/
    │   ├── mongodb/
    │   ├── rabbitmq/
    │   └── storage-class/
    └── tests/
```
Where:
- **terraform/live/**: Main folder containing Terragrunt files.
- **_envcommon/**: Folder contains common configurations across all MQ services.
- **kafka/**: At the top level are each of MQ services such as Kafka, Rabbitmq. This specific folder contains the configuration for Kafka.
- **_mqcommon/**: Folder contains the common configuration across specific MQ. The commmon configuration will be applied across every cloud provider for Kafka MQ service. 
- **aws/**: Within each MQ service there will be one or more cloud providers, such as [AWS](https://aws.amazon.com/) or [GCP](https://cloud.google.com/). Within each cloud provider you deploy all the resources for that cloud provider - for example the [EKS AWS](https://aws.amazon.com/eks/) and [Kubernetes](https://kubernetes.io/pl/) resources. 
- **eks/**: Folder contains the [EKS AWS](https://aws.amazon.com/eks/) service configuration defined in a terragrunt.hcl file.
- **prometheus/**: Folder contains the [kube-prometheus-stack](https://github.com/prometheus-community/helm-charts) Helm chart configuration defined in a terragrunt.hcl file.
- **kafka/**: Folder contains [Strimzi Kafka](https://strimzi.io/) cluster-operator and Kafka cluster Helm charts configuration defined in a terragrunt.hcl file.
- **storage-class/**: Folder contains configuration for the [AWS GP3 Storage Class](https://docs.aws.amazon.com/eks/latest/userguide/ebs-csi.html) configuration.
- **k8s_providers.hcl**: Terragrunt file containing configuration for the [Kubernetes](https://registry.terraform.io/providers/hashicorp/kubernetes/latest/docs) and [Helm](https://registry.terraform.io/providers/hashicorp/helm/latest/docs) providers.
- **terragrunt.hcl**: Terragrunt file containing configuration for the [AWS](https://registry.terraform.io/providers/hashicorp/aws/latest/docs) provider
- **modules/**: Folder contains resuable Terraform modules.


## Kafka KRaft MQ
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

## Storage configuration
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
## Using python script
`terraform\script.py` provides an automated way of performing terragrunt actions against configuration stored in json file:
#### Command
```bash
python3 script.py [terragrunt action] [json file]
```
```
[terragrunt action]: plan, apply, destroy
```
#### Json file

```json
{
    "instance": {
        "cloudprovider": "[cloud_provider]",
        "bucket_name": "[cloud_bucket_name]",
        "mq": "[mq_name]",
        "cluster_name": "[cluster_name]",
        "nodes_number": "[number_of_nodes]"
    }
}
```
||Accepted values|
|---|---|
|[cloud_provider]|"aws" , "gcp" , "az"|
|[cloud_bucket_name]|"s3-bucket-mqperf" , "gcs-bucket-mqperf" , "az-bucket-mqperf"|
|[mq_name]|"kafka" , "rabbitmq"|
|[cluster_name]|str| 
|[number_of_nodes]|str|
### Example command
```
python3 script.py apply example-data.json
```
### [example-data.json](https://github.com/softwaremill/mqperf/blob/workspace-listing/terraform/example-data.json)
```json
{
    "instance": {
        "cloudprovider": "aws",
        "bucket_name": "s3-bucket-mqperf",
        "mq": "rabbitmq",
        "cluster_name": "rabbit01",
        "nodes_number": "4"
    }
}
```




 ## Copyright

Copyright (C) 2013-2022 SoftwareMill [https://softwaremill.com](https://softwaremill.com).
