terraform {
  source = "git::https://github.com/softwaremill/terraform-eks-bootstrap//?ref=v0.0.1"
}

include "root" {
  path = find_in_parent_folders()
}

inputs = {

  org         = "SML"
  environment = "test"

  eks_cluster_node_groups = {
    additional = {
      min_size       = 3
      max_size       = 3
      desired_size   = 3
      instance_types = ["t3.large"]
    }
  }
  vpc_cidr         = "10.1.0.0/16"
  eks_cluster_name = "mqperf-cluster"
}
