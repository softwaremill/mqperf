resource "helm_release" "kube_prometheus_stack" {
  name       = var.name
  repository = var.repository
  chart      = var.chart
  version    = var.chart_version
}
