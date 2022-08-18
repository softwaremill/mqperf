dependency "eks" {
  config_path = "../eks"
  mock_outputs = {
    eks_cluster_endpoint                   = "temp-endpoint"
    eks_cluster_certificate_authority_data = "dGVzdA=="
    eks_cluster_id                         = "temp-id"
  }
  mock_outputs_merge_strategy_with_state = "shallow"
}

include "root" {
  path = find_in_parent_folders()
}

generate "provider" {
  path      = "provider.tf"
  if_exists = "overwrite_terragrunt"
  contents  = <<EOF
terraform {
  required_providers {
    helm = {
      source  = "hashicorp/helm"
      version = "2.6.0"
    }
    kubectl = {
      source = "gavinbunney/kubectl"
      version = "1.14.0"
    }
  }
}

data "aws_eks_cluster_auth" "eks" {
  name = "${dependency.eks.outputs.eks_cluster_id}"
}

provider "kubectl" {
    host                   = "${dependency.eks.outputs.eks_cluster_endpoint}"
    cluster_ca_certificate = base64decode("${dependency.eks.outputs.eks_cluster_certificate_authority_data}")
    token                  = data.aws_eks_cluster_auth.eks.token
}

provider "helm" {
  kubernetes {
    host                   = "${dependency.eks.outputs.eks_cluster_endpoint}"
    cluster_ca_certificate = base64decode("${dependency.eks.outputs.eks_cluster_certificate_authority_data}")
    token                  = data.aws_eks_cluster_auth.eks.token
    }
}
EOF
}
