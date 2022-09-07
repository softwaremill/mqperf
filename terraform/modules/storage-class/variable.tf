variable "eks_storage_classes" {
  description = "EBS storage class with custom parameters"
  type = list(object({
    name                      = string
    storage_class_provisioner = string
    parameters                = optional(map(string))
    volume_binding_mode       = optional(string)
    reclaim_policy            = optional(string)

    }
  ))
  default = []
}

variable "aws_account_id" {
  type             = string
  descrdescription = "Account ID of the current user for the AWS"
}

variable "oidc_provider_url" {
  type        = string
  description = "The URL on the EKS cluster for the OpenID Connect identity provider"
}

variable "cluster_name" {
  type        = string
  description = "Name of the AWS EKS cluster"
}
