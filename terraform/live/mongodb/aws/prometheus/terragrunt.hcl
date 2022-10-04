include "root" {
  path = find_in_parent_folders()
}

include "k8s_providers" {
  path = "${dirname(find_in_parent_folders())}/k8s_providers.hcl"
}

include "envcommon" {
  path = "${dirname(find_in_parent_folders())}/../../_envcommon/prometheus.hcl"
}

dependency "eks" {
  config_path = "../eks"
  mock_outputs = {
    eks_cluster_endpoint                   = "temp-endpoint"
    eks_cluster_certificate_authority_data = "dGVzdA=="
    eks_cluster_id                         = "temp-id"
  }
  mock_outputs_merge_strategy_with_state = "shallow"
}
