include "root" {
  path = find_in_parent_folders()
}

include "k8s_providers" {
  path = "${dirname(find_in_parent_folders())}/k8s_providers.hcl"
}

include "envcommon" {
  path = "${dirname(find_in_parent_folders())}/../../_envcommon/prometheus.hcl"
}

dependency "gke" {
  config_path = "../gke"
  mock_outputs = {
    gke_endpoint       = "temp-endpoint"
    gke_ca_certificate = "dGVzdA=="
  }
  mock_outputs_merge_strategy_with_state = "shallow"
}
