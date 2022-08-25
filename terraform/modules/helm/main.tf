resource "helm_release" "custom_helm_release" {
  name       = var.release_name
  repository = var.repository
  chart      = var.chart_name
  version    = var.chart_version
  values     = var.values

  dynamic "set" {
    for_each = var.sets
    content {
      name  = set.value.name
      value = set.value.value
    }
  }
}
