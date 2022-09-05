locals {
  cluster_name = "mqperf-cluster"
  region       = "eu-central-1"
}

remote_state {
  backend = "s3"
  config = {
    encrypt        = true
    bucket         = get_env("TF_VAR_BUCKET_NAME")
    key            = "${path_relative_to_include()}/terraform.tfstate"
    region         = get_env("TF_VAR_AWS_REGION", "eu-central-1")
    dynamodb_table = get_env("TF_VAR_DYNAMODB_TABLE")
  }
  generate = {
    path      = "backend.tf"
    if_exists = "overwrite_terragrunt"
  }
}

generate "provider" {
  path      = "provider.tf"
  if_exists = "overwrite_terragrunt"
  contents  = <<EOF
terraform {
  required_providers {
    aws = {
      version = ">= 4.15.1"
      source = "hashicorp/aws"
    } 
    kubernetes = {
      source  = "hashicorp/kubernetes"
      version = "2.12.1"
    }


  }
}

data "aws_eks_cluster" "eks" {
        name = ${get_env("CLUSTER_NAME", local.cluster_name)}
    }

data "aws_eks_cluster_auth" "eks" {
        name = ${get_env("CLUSTER_NAME", local.cluster_name)}
    }



provider "aws" {
    region = "${local.region}"
}

provider "kubernetes" {
        host                   = data.aws_eks_cluster.eks.endpoint
        cluster_ca_certificate = base64decode(data.aws_eks_cluster.eks.certificate_authority[0].data)
        token                  = data.aws_eks_cluster_auth.eks.token
    }

EOF
}
