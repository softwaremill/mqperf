variable "replicas_number" {
  type        = number
  description = "Number of replicas"
  default     = 3
}

variable "cpu_request" {
  type        = string
  description = "CPU requirements in CPU units"
  default     = "1000m"
}

variable "memory_request" {
  type        = string
  description = "Memory requirements in bytes"
  default     = "2Gi"
}

variable "cpu_limit" {
  type        = string
  description = "CPU resource limit in CPU units"
  default     = "1000m"
}

variable "memory_limit" {
  type        = string
  description = "Memory resource limit in bytes"
  default     = "2Gi"
}

variable "chart_version" {
  type        = string
  description = "Define the Strimzi Kafka Operator version"
  default     = "2.7.1"
}

variable "sets" {
  type    = list(map(any))
  default = []
}


