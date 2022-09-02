terraform {
  source = "git::https://github.com/softwaremill/terraform-eks-bootstrap//?ref=v0.0.1"

  before_hook "select workspace" {
    commands = ["plan", "state", "apply", "destroy", "refresh"]
    execute  = ["${dirname(find_in_parent_folders())}/../../../workspace-hook.sh", get_env("CLUSTER_NAME")]
  }
}

inputs = {

  org         = "SML"
  environment = "test"

  eks_cluster_node_groups = {
    default = {
      min_size       = 3
      max_size       = 3
      desired_size   = 3
      instance_types = ["t3.large"]
    }
  }
  vpc_cidr         = "10.1.0.0/16"
  eks_cluster_name = get_env("CLUSTER_NAME")
}
