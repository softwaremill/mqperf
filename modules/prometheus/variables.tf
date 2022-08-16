variable "name" {
  type    = string
  default = "kube-prometheus-stack"
}

variable "repository" {
  type    = string
  default = "https://prometheus-community.github.io/helm-charts"
}

variable "chart" {
  type    = string
  default = "kube-prometheus-stack"
}

variable "chart_version" {
  type    = string
  default = "39.6.0"
}
