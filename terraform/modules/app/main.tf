resource "kubectl_manifest" "app" {
  yaml_body = templatefile("app-manifests/app.yaml", {
  "APP_IMAGE" = var.app_image, "APP_MAX_NODES_NUMBER" = var.app_max_nodes_number })
}

resource "kubectl_manifest" "service" {
  yaml_body = file("app-manifests/service.yaml")
}

resource "kubectl_manifest" "servicemonitor" {
  yaml_body = file("app-manifests/servicemonitor.yaml")
}

resource "kubernetes_config_map" "grafana_dashboards" {
  metadata {
    name = "app-dashboards"
    labels = {
      "grafana_dashboard" = "1"
    }
  }

  data = {
    "mqperf.json" = "${templatefile("grafana-dashboards/mqperf.json", { "DS_PROMETHEUS" = "default" })}"
  }
}
