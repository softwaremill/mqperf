terraform {
  source = "git::https://github.com/softwaremill/terraform-eks-bootstrap//?ref=v0.0.2"
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
  eks_cluster_name = "mqperf-cluster"
  eks_additional_cluster_addons = {
    aws-ebs-csi-driver = {
      resolve_conflicts        = "OVERWRITE"
      service_account_role_arn = "arn:aws:iam::${get_aws_account_id()}:role/AmazonEKS_EBS_CSI_DriverRole"
    }
  }
  eks_storage_classes = [
    {
      name                      = "mqperf-storageclass"
      storage_class_provisioner = "ebs.csi.aws.com"
      volume_binding_mode       = "WaitForFirstConsumer"
      parameters = {
        type   = "gp3"
        fsType = "ext4"
      }

    }

  ]
}
