module "helm" {
  source        = "./helm//."
  release_name  = "rabbitmq-operator"
  repository    = "https://charts.bitnami.com/bitnami"
  chart_name    = "rabbitmq-cluster-operator"
  chart_version = var.chart_version
  sets          = var.sets
}


resource "kubectl_manifest" "rabbitmq" {

  yaml_body = templatefile("rabbitmq-manifests/rabbitmq.yaml", {
    "REPLICAS_NUMBER" = var.replicas_number, "CPU_REQUEST" = var.cpu_request,
  "MEMORY_REQUEST" = var.memory_request, "CPU_LIMIT" = var.cpu_limit, "MEMORY_LIMIT" = var.memory_limit })

  depends_on = [
    module.helm.release_name,
  ]
}
