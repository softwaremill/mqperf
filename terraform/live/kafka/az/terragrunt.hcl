remote_state {
  backend = "azurerm"
  config = {
    resource_group_name  = ""
    storage_account_name = ""
    container_name       = get_env("TF_VAR_BUCKET_NAME")
    key                  = "${path_relative_to_include()}/terraform.tfstate"
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
}

EOF
}

