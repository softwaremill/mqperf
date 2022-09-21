variable "release_name" {
  type        = string
  description = "Release name"
}

variable "repository" {
  type        = string
  description = "Repository URL where to locate the requested chart"
}

variable "chart_name" {
  type        = string
  description = "Chart name to be installed"
}

variable "chart_version" {
  type        = string
  description = "Specify the exact chart version to install. If this is not specified, the latest version is installed"
}

variable "sets" {
  type        = list(map(any))
  default     = []
  description = "Dynamic set block with custom values"
}

variable "values" {
  type    = list(any)
  default = []
}
