module "helm" {
  source        = "../helm"
  release_name  = "postgresql-operator"
  repository    = "https://opensource.zalando.com/postgres-operator/charts/postgres-operator"
  chart_name    = "postgres-operator"
  chart_version = var.chart_version
  sets          = var.sets
}

resource "kubectl_manifest" "postgres-cluster" {
  depends_on = [
    module.helm
  ]

  yaml_body = templatefile("postgresql-manifests/postgres-cluster.yaml", {
    "REPLICAS_NUMBER" = var.replicas_number, "STORAGE_CLASS" = var.storage_class,
    "STORAGE_SIZE"    = var.storage_size, "CPU_REQUEST" = var.cpu_request,
    "MEMORY_REQUEST"  = var.memory_request
  })
}
