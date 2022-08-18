locals {
  cluster_name = "mqperf-cluster"
  region       = "eu-central-1"
}


remote_state {
  backend = "s3"
  config = {
    encrypt        = true
    bucket         = "ab-new-bucket"
    key            = "${path_relative_to_include()}/terraform.tfstate"
    region         = "eu-central-1"
    dynamodb_table = "ab-new-table"
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
  }
}
provider "aws" {
    region = "${local.region}"
}

EOF
}

