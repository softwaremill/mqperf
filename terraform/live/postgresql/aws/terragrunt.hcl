locals {
  cluster_name = "mqperf-cluster"
  region       = "eu-central-1"
}

remote_state {
  backend = "s3"
  config = {
    encrypt = true
    bucket  = get_env("TF_VAR_BUCKET_NAME")
    key     = "${path_relative_to_include()}/terraform.tfstate"
    region  = get_env("TF_VAR_AWS_REGION", "eu-central-1")
  }
  generate = {
    path      = "backend.tf"
    if_exists = "overwrite_terragrunt"
  }
}

generate "provider" {
  path      = "provider.tf"
  if_exists = "skip"
  contents  = <<EOF
terraform {
  required_providers {
    aws = {
      version = ">= 4.15.1"
      source  = "hashicorp/aws"
    }

  }
}

provider "aws" {
    region = "${local.region}"
}

EOF
}
