terraform {
  source = "../../../../modules/rabbitmq//."
}

dependency "prometheus" {
  config_path  = "../prometheus"
  skip_outputs = true
}
