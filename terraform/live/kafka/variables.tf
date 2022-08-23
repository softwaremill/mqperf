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
