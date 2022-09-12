# mqperf v2
## Overview

## Prerequisites
1. Install [Terraform](https://www.terraform.io/) version 0.38.7 or newer.
2. Install [Terragrunt](https://terragrunt.gruntwork.io/) version v1.2.6 or newer.
### Configure the cloud provider
#### AWS
1. Create the [AWS IAM](https://docs.aws.amazon.com/cli/latest/userguide/getting-started-prereqs.html) user and install the [AWS CLI](https://docs.aws.amazon.com/cli/latest/userguide/cli-chap-getting-started.html).
2. Configure basic settings for the AWS CLI. Use the `aws configure` CLI command. You will be prompted for configuration values such as your AWS Access Key Id, your AWS Secret Access Key and the deafult AWS Region. See the [Quick setup](https://docs.aws.amazon.com/cli/latest/userguide/getting-started-quickstart.html) documentation for more details.

#### GCP
1.
2.

### Set environment variables
Set environment variables required for the bootstraping script:
- `TF_VAR_BUCKET_NAME` - name of the [S3 AWS bucket](https://docs.aws.amazon.com/s3/index.html).
- `TF_VAR_AWS_REGION` - name of the AWS Region.

## Quick start 
Bootstrap the Kubernetes cluster using the cloud provider and the MQ of choice alongside the [kube-prometheus-stack](https://github.com/prometheus-community/helm-charts/tree/main/charts/kube-prometheus-stack).


## The file/folder structure
In this repo, Terragrunt is used to keep configurations DRY and to work with multiple Terraform modules. Follow the [Terragrunt](https://terragrunt.gruntwork.io/docs/) documentation to learn more.

Each folder consists of just one terragrunt.hcl file per component. The terragrunt.hcl files contain the source URL of the module to deploy and the inputs to set for that module in the current folder. It also contains dependencies and definitions for the providers.

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
    │   └── rabbitmq/
    └── modules/
        ├── helm/
        ├── kafka/
        ├── rabbitmq/
        └── storage-class/
```
Where:
- **terraform/**: Main folder containing terraform and terragrunt files
- **live/**: 
- **_envcommon**: Folder contains the common configurations across all MQ services
- **kafka/**: Folder contains the configuration for Kafka
- **_mqcommon**: Folder contains the common configuration across specific MQ, in this case Kafka
- **aws/**: Within each cloud provider you deploy all the resources for that cloud provider
- **eks/**: Folder contains the [EKS AWS](https://aws.amazon.com/eks/) service configuration defined in a terragrunt.hcl file
- **prometheus/**: Folder contains the [kube-prometheus-stack](https://github.com/prometheus-community/helm-charts) Helm chart configuration defined in a terragrunt.hcl file
- **kafka/**: Folder contains [Strimzi Kafka](https://strimzi.io/) cluster-operator and Kafka cluster Helm charts configuration defined in a terragrunt.hcl file
- **storage-class**: Folder contains configuration for the [AWS GP3 Storage Class](https://docs.aws.amazon.com/eks/latest/userguide/ebs-csi.html) configuration 
- **k8s_providers.hcl**: Terragrunt file containing configuration for the [Kubernetes](https://registry.terraform.io/providers/hashicorp/kubernetes/latest/docs) and [Helm](https://registry.terraform.io/providers/hashicorp/helm/latest/docs) providers
- **terragrunt.hcl**: Terragrunt file containing configuration for the [AWS](https://registry.terraform.io/providers/hashicorp/aws/latest/docs) provider
- **modules/**: Folder contains resuable modules 


## Kafka KRaft MQ
To use the [Kafka KRaft](https://strimzi.io/docs/operators/in-development/configuring.html#ref-operator-use-kraft-feature-gate-str) feature, override these variables from the `terraform/live/kafka/variables.tf` file with the values specified below:
```
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
