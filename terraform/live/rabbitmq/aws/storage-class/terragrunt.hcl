terraform {
  source = "../../../../modules/storage-class//."

  before_hook "select workspace" {
    commands = ["plan", "state", "apply", "destroy", "refresh"]
    execute  = ["${dirname(find_in_parent_folders())}/../../../workspace-hook.sh", get_env("CLUSTER_NAME")]
  }
}
include "root" {
  path = find_in_parent_folders()
}

include "k8s_providers" {
  path = "${dirname(find_in_parent_folders())}/k8s_providers.hcl"
}

dependency "eks" {
  config_path = "../eks"
  mock_outputs = {
    eks_cluster_endpoint                   = "temp-endpoint"
    eks_cluster_certificate_authority_data = "dGVzdA=="
    eks_cluster_id                         = "temp-id"
    eks_cluster_oidc_issuer_url            = "mock-provider"
  }
  mock_outputs_merge_strategy_with_state = "shallow"
}

inputs = {
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
  aws_account_id    = get_aws_account_id()
  oidc_provider_url = replace(dependency.eks.outputs.eks_cluster_oidc_issuer_url, "https://", "")
  cluster_name      = get_env("CLUSTER_NAME")

}

