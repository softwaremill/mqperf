terraform {

  after_hook "after_hook" {
    commands = ["apply"]
    execute  = ["${dirname(find_in_parent_folders())}/../../../after-hook.sh"]
  }
}
