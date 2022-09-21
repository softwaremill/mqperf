variable "chart_version" {
  type        = string
  description = "Define the Postgres Operator version"
  default     = "1.8.2"
}

variable "sets" {
  type = list(map(any))
  default = [{
    name  = "nodeSelector.node-group"
    value = "controllers"
    },
    {
      name  = "enable_pod_antiaffinity"
      value = "true"
    },
    {
      name  = "configPostgresPodResources.default_memory_limit"
      value = "4Gi" #increase the default limit
    }
  ]
}

variable "replicas_number" {
  type        = number
  description = "Number of replicas"
  default     = 3
}

variable "storage_size" {
  type        = string
  description = "The capacity of the persistent volume, expressed as a Kubernetes resource quantity."
  default     = "20Gi"
}

variable "storage_class" {
  type        = string
  description = "The name of the Kubernetes StorageClass to use"
  default     = "standard" # default storageclass name for GCP
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
