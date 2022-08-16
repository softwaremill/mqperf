variable "name" {
  type    = string
  default = "kafka-operator"
}

variable "repository" {
  type    = string
  default = "https://strimzi.io/charts/"
}

variable "chart" {
  type    = string
  default = "strimzi-kafka-operator"
}

variable "chart_version" {
  type    = string
  default = "0.30.0"
}
