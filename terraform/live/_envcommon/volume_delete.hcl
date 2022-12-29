terraform {

  after_hook "delete volume hook" {
    commands = ["apply"]
    execute  = ["${dirname(find_in_parent_folders())}/../../../volume-hook.sh"]
  }
}
