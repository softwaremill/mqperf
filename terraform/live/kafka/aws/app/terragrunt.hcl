terraform {
  source = "../../../../modules/app//."

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
  app_image = get_env("APP_IMAGE")
}
