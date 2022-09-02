terraform {
  source = "git::https://github.com/softwaremill/terraform-gke-bootstrap//"

  before_hook "select workspace" {
    commands = ["plan", "state", "apply", "destroy", "refresh"]
    execute  = ["${dirname(find_in_parent_folders())}/../../../workspace-hook.sh", get_env("CLUSTER_NAME")]
  }
}

inputs = {
  region           = "${get_env("TF_VAR_GCP_REGION", "europe-central2")}"
  create_project   = false
  project_id       = get_env("TF_VAR_GCP_PROJECT")
  platform_name    = "test"
  subnet_network   = "10.1.0.0/16"
  k8s_network_base = "10.100.0.0/16"
  regional         = false
  zones            = ["europe-central2-a"]
  node_pools = [
    {
      name         = "default-pool"
      disk_size_gb = 50
      max_count    = 3
      preemptible  = true
    }
  ]
}
