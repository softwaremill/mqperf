terraform {
  source = "../../../../modules/kafka//."

  before_hook "select workspace" {
    commands = ["plan", "state", "apply", "destroy", "refresh"]
    execute  = ["${dirname(find_in_parent_folders())}/../../../workspace-hook.sh", get_env("CLUSTER_NAME")]
  }
}

dependency "prometheus" {
  config_path  = "../prometheus"
  skip_outputs = true
}
