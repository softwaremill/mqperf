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

variable "chart_version" {
  type        = string
  description = "Define the Strimzi Kafka Operator version"
  default     = "2.7.1"
}

variable "sets" {
  type    = list(map(any))
  default = []
}

variable "storage_size" {
  type        = string
  description = "The capacity of the persistent volume, expressed as a Kubernetes resource quantity."
  default     = "20Gi"
}

variable "storage_class" {
  type        = string
  description = "The name of the Kubernetes StorageClass to use"
  default     = "standard"
}
