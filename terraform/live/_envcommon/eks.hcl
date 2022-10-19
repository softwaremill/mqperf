terraform {
  source = "git::https://github.com/softwaremill/terraform-eks-bootstrap//?ref=v0.0.3"


  before_hook "select workspace" {
    commands = ["plan", "state", "apply", "destroy", "refresh"]
    execute  = ["${dirname(find_in_parent_folders())}/../../../workspace-hook.sh", get_env("CLUSTER_NAME")]
  }
}

inputs = {

  org         = "SML"
  environment = get_env("CLUSTER_NAME")

  eks_cluster_node_groups = {
    controllers-pool = {
      min_size       = 1
      max_size       = get_env("CONTROLLER_NODES_NUMBER")
      desired_size   = 1
      instance_types = ["${get_env("CONTROLLER_NODES_TYPE")}"]
      labels = {
        node-group = "controllers"
      }
    },
    queues-pool = {
      min_size       = 3
      max_size       = get_env("QUEUE_NODES_NUMBER")
      desired_size   = 3
      instance_types = ["${get_env("QUEUE_NODES_TYPE")}"]
      labels = {
        node-group = "queues"
      }
    },
    app-pool = {
      min_size       = 1
      max_size       = get_env("APP_NODES_NUMBER")
      desired_size   = 1
      instance_types = ["${get_env("APP_NODES_TYPE")}"]
      labels = {
        node-group = "apps"
      }
    }
  }
  vpc_cidr         = "10.1.0.0/16"
  eks_cluster_name = get_env("CLUSTER_NAME")

  eks_additional_cluster_addons = {
    aws-ebs-csi-driver = {
      resolve_conflicts        = "OVERWRITE"
      service_account_role_arn = "arn:aws:iam::${get_aws_account_id()}:role/AmazonEKS_EBS_CSI_DriverRole_${get_env("CLUSTER_NAME")}"
    }
  }

}
