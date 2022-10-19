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
    azurerm = {
      source = "hashicorp/azurerm"
      version = "3.24.0"
    }
  }
}

provider "azurerm" {
  features {}
}

EOF
}

