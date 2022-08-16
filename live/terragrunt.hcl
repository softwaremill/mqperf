locals {
  cluster_name = "mqperf-cluster"
  common_vars  = read_terragrunt_config("common.hcl")
}


remote_state {
  backend = "s3"
  config = {
    encrypt        = true
    bucket         = local.common_vars.locals.bucket_name
    key            = "${path_relative_to_include()}/terraform.tfstate"
    region         = local.common_vars.locals.aws_region
    dynamodb_table = "terraform-locks"
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



