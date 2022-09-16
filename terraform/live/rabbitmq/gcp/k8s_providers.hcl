generate "k8s_providers" {
  path      = "provider.tf"
  if_exists = "overwrite_terragrunt"
  contents  = <<EOF
terraform {
  required_providers {
    google = {
      source  = "hashicorp/google"
      version = "4.33.0"
    }
    helm = {
      source  = "hashicorp/helm"
      version = "2.6.0"
    }
    kubectl = {
      source = "gavinbunney/kubectl"
      version = "1.14.0"
    }
    kubernetes = {
      source  = "hashicorp/kubernetes"
      version = "2.12.1"
    }
  }
}

provider "google" {
  project     = "${get_env("TF_VAR_GCP_PROJECT")}"
  region      = "${get_env("TF_VAR_GCP_REGION", "europe-central2")}"
}

data "google_client_config" "provider" {}

provider "kubectl" {
  host                   = "https://${dependency.gke.outputs.gke_endpoint}"
  cluster_ca_certificate = base64decode("${dependency.gke.outputs.gke_ca_certificate}")
  token                  = data.google_client_config.provider.access_token
  load_config_file       = false
}

provider "helm" {
  kubernetes {
    host                   = "https://${dependency.gke.outputs.gke_endpoint}"
    cluster_ca_certificate = base64decode("${dependency.gke.outputs.gke_ca_certificate}")
    token                  = data.google_client_config.provider.access_token
    }
}

provider "kubernetes" {
  host                   = "https://${dependency.gke.outputs.gke_endpoint}"
  cluster_ca_certificate = base64decode("${dependency.gke.outputs.gke_ca_certificate}")
  token                  = data.google_client_config.provider.access_token
}

EOF
}
