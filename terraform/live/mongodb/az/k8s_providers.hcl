generate "k8s" {
  path      = "provider.tf"
  if_exists = "overwrite_terragrunt"
  contents  = <<EOF
terraform {
  required_providers {
    azurerm = {
      source  = "hashicorp/azurerm"
      version = "3.27.0"
    }
    helm = {
      source  = "hashicorp/helm"
      version = "2.6.0"
    }
    kubectl = {
      source  = "gavinbunney/kubectl"
      version = "1.14.0"
    }
    kubernetes = {
      source  = "hashicorp/kubernetes"
      version = "2.12.1"
    }
  }
}

provider "azurerm" {
  features {}
}

provider "kubectl" {
  host                   = "${dependency.aks.outputs.host}"
  client_certificate     = base64decode("${dependency.aks.outputs.client_certificate}")
  client_key             = base64decode("${dependency.aks.outputs.client_key}")
  cluster_ca_certificate = base64decode("${dependency.aks.outputs.cluster_ca_certificate}")
  load_config_file       = false
}

provider "helm" {
  kubernetes {
    host                   = "${dependency.aks.outputs.host}"
    client_certificate     = base64decode("${dependency.aks.outputs.client_certificate}")
    client_key             = base64decode("${dependency.aks.outputs.client_key}")
    cluster_ca_certificate = base64decode("${dependency.aks.outputs.cluster_ca_certificate}")
  }
}

provider "kubernetes" {
  host                   = "${dependency.aks.outputs.host}"
  client_certificate     = base64decode("${dependency.aks.outputs.client_certificate}")
  client_key             = base64decode("${dependency.aks.outputs.client_key}")
  cluster_ca_certificate = base64decode("${dependency.aks.outputs.cluster_ca_certificate}")
}

EOF
}
