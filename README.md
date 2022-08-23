# mqperf v2
## Overview

## Prerequisites
1. Install [Terraform](https://www.terraform.io/) version 0.38.7 or newer.
2. Install [Terragrunt](https://terragrunt.gruntwork.io/) version v1.2.6 or newer.

## Quick start - deploying all components
Bootstrap the AWS EKS cluster alongside the [Apache Kafka cluster by Strimzi](https://strimzi.io/) and the [kube-prometheus-stack](https://github.com/prometheus-community/helm-charts/tree/main/charts/kube-prometheus-stack).

1. Clone the repository.
2. Navigate to the live root folder `cd live`.
3. Run `terragrunt run-all plan` to preview the changes.
4. Run `terragrunt run-all apply` to execute the actions proposed in the plan.

By running the ‘run-all’ command, Terragrunt recursively looks through all the subfolders of the current working directory, finds all folders with a terragrunt.hcl file and runs terragrunt apply in each of those folders concurrently.

## Deploying a single component
1. Navigate to the single component/app folder (e.g. live/eks) `cd live/eks`.
2. Run `terragrunt plan` to preview the changes
3. Run `terragrunt apply` to execute the actions.

## The file/folder structure
In this repo, Terragrunt is used to keep configurations DRY and to work with multiple Terraform modules. Follow the [Terragrunt](https://terragrunt.gruntwork.io/docs/) documentation to learn more.

Each folder consists of just one terragrunt.hcl file per component. The terragrunt.hcl files contain the source URL of the module to deploy and the inputs to set for that module in the current folder. It also contains dependencies and definitions for the providers.

- The EKS folder contains the code necessary to bootstrap the AWS EKS cluster.
- The Kafka folder contains the code necessary to deploy the Apache Kafka cluster on Kubernetes by [Strimzi](https://strimzi.io/).
- The Prometheus folder contains the code necessary to deploy the [kube-prometheus-stack](https://github.com/prometheus-community/helm-charts/tree/main/charts/kube-prometheus-stack) Helm chart. 
