variable "replicas_number" {
  type        = number
  description = "Number of replicas"
  default     = 3
}

variable "delete_pvc_claim" {
  type        = bool
  description = "Define if PVC should be deleted"
  default     = true
}

variable "chart_version" {
  type        = string
  description = "Define the Strimzi Kafka Operator version"
  default     = "0.30.0"
}

variable "sets" {
  type    = list(map(any))
  default = []
}

variable "kafka_kraft_enabled" {
  type        = bool
  description = "Enable Kafka KRaft"
  default     = false
}