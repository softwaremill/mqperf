terraform {

  before_hook "delete volume hook" {
    commands = ["destroy"]
    execute  = ["${dirname(find_in_parent_folders())}/../../../volume-hook.sh"]
  }
}

inputs = {
  storage_class = "mqperf-storageclass"
  storage_size  = "20Gi"
}
