module "helm" {
  source        = "./helm//."
  release_name  = "mongodb-operator"
  repository    = "https://charts.bitnami.com/bitnami"
  chart_name    = "mongodb"
  chart_version = var.chart_version
  sets          = var.sets
}


resource "kubectl_manifest" "mongodb" {

  yaml_body = templatefile("mongodb-manifests/mongodb.yaml", {
    "REPLICAS_NUMBER" = var.replicas_number, "CPU_REQUEST" = var.cpu_request,
  "MEMORY_REQUEST" = var.memory_request, "STORAGE_SIZE" = var.storage_size, "STORAGE_CLASS" = var.storage_class })

  depends_on = [
    module.helm
  ]
}
