terraform {
  source = "../../../../modules/kafka//."
}

dependency "prometheus" {
  config_path  = "../prometheus"
  skip_outputs = true
}
