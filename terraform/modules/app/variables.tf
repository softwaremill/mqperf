variable "app_image" {
  type        = string
  description = "Client app image from config file"
}

variable "app_max_nodes_number" {
  type = number
  description = "Number of nodes client app should be deployed on"
  default = 1
}
