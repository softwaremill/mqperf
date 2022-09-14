module "helm" {
  source        = "./helm//."
  release_name  = "kafka-operator"
  repository    = "https://strimzi.io/charts/"
  chart_name    = "strimzi-kafka-operator"
  chart_version = var.chart_version
  sets          = var.sets
}

resource "kubectl_manifest" "kafka_metrics_config" {
  yaml_body = file("kafka-manifests/kafka-metrics.yaml")

  depends_on = [
    module.helm
  ]
}

resource "kubectl_manifest" "kafka" {

  yaml_body = (var.kafka_kraft_enabled) ? templatefile("kafka-manifests/kafka-kraft.yaml", { "REPLICAS_NUMBER" = var.replicas_number, "DELETE_PVC" = var.delete_pvc_claim }) : templatefile("kafka-manifests/kafka.yaml", { "REPLICAS_NUMBER" = var.replicas_number, "DELETE_PVC" = var.delete_pvc_claim, "STORAGE_SIZE" = var.storage_size, "STORAGE_CLASS" = var.storage_class })

  depends_on = [
    module.helm.release_name, kubectl_manifest.kafka_metrics_config
  ]
}

resource "kubectl_manifest" "kafka_resources_metrics" {
  yaml_body = file("kafka-manifests/kafka-podmonitor.yaml")
}

resource "kubernetes_config_map" "grafana_dashboards" {
  metadata {
    name = "kafka-dashboards"
    labels = {
      "grafana_dashboard" = "1"
    }
  }

  data = {
    "strimzi-kafka-exporter" = "${templatefile("grafana-dashboards/strimzi-kafka-exporter.json", { "DS_PROMETHEUS" = "default" })}"
    "strimzi-kafka.json"     = "${templatefile("grafana-dashboards/strimzi-kafka.json", { "DS_PROMETHEUS" = "default" })}"
    "strimzi-zookeeper.json" = "${templatefile("grafana-dashboards/strimzi-zookeeper.json", { "DS_PROMETHEUS" = "default" })}"
  }
}

