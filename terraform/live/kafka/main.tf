module "helm" {
  source        = "../../modules/helm//."
  release_name  = "kafka-operator"
  repository    = "https://strimzi.io/charts/"
  chart_name    = "strimzi-kafka-operator"
  chart_version = "0.30.0"
}

resource "kubectl_manifest" "kafka_metrics_config" {
  yaml_body = file("kafka-manifests/kafka-configmap.yaml")

  depends_on = [
    module.helm
  ]
}

resource "kubectl_manifest" "kafka" {
  yaml_body = templatefile("kafka-manifests/kafka.yaml", { "REPLICAS_NUMBER" = 3, "DELETE_PVC" = true })

  depends_on = [
    module.helm.release_name, kubectl_manifest.kafka_metrics_config
  ]
}

resource "kubectl_manifest" "kafka_resources_metrics" {
  yaml_body = file("kafka-manifests/kafka-metrics.yaml")
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

