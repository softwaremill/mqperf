include "root" {
  path = find_in_parent_folders()
}

include "k8s_providers" {
  path = "${dirname(find_in_parent_folders())}/k8s_providers.hcl"
}

include "envcommon" {
  path = "${dirname(find_in_parent_folders())}/../../_envcommon/prometheus.hcl"
}

dependency "aks" {
  config_path = "../aks"
  mock_outputs = {
    cluster_name        = "temp-name"
    resource_group_name = "temp-name"
    host                = "temp-host"
  }
  mock_outputs_merge_strategy_with_state = "shallow"
}
