module "helm" {
  source        = "../../modules/helm//."
  release_name  = "kafka-operator"
  repository    = "https://strimzi.io/charts/"
  chart_name    = "strimzi-kafka-operator"
  chart_version = "0.30.0"
}

resource "kubectl_manifest" "kafka" {
  yaml_body = <<YAML
apiVersion: kafka.strimzi.io/v1beta2
kind: Kafka
metadata:
  name: my-cluster
spec:
  kafka:
    template:
      pod:
        metadata:
          labels:
            app: kafka
        affinity:
          podAntiAffinity:
            requiredDuringSchedulingIgnoredDuringExecution:
              - labelSelector:
                  matchExpressions:
                    - key: app
                      operator: In
                      values:
                      - kafka
                topologyKey: "kubernetes.io/hostname"
    version: 3.2.0
    replicas: ${var.replicas_number}
    listeners:
      - name: plain
        port: 9092
        type: internal
        tls: false
      - name: tls
        port: 9093
        type: internal
        tls: true
    config:
      offsets.topic.replication.factor: 3
      transaction.state.log.replication.factor: 3
      transaction.state.log.min.isr: 2
      default.replication.factor: 3
      min.insync.replicas: 2
      inter.broker.protocol.version: "3.2"
    storage:
      type: jbod
      volumes:
      - id: 0
        type: persistent-claim
        size: 20Gi
        deleteClaim: ${var.delete_pvc_claim}
  zookeeper:
    template:
      pod:
        metadata:
          labels:
            app: zookeeper
        affinity:
          podAntiAffinity:
            requiredDuringSchedulingIgnoredDuringExecution:
              - labelSelector:
                  matchExpressions:
                    - key: app
                      operator: In
                      values:
                      - zookeeper
                topologyKey: "kubernetes.io/hostname"
    replicas: ${var.replicas_number}
    storage:
      type: persistent-claim
      size: 20Gi
      deleteClaim: ${var.delete_pvc_claim}
  entityOperator:
    topicOperator: {}
    userOperator: {}
YAML


  depends_on = [
    module.helm.release_name
  ]
}

resource "kubernetes_config_map" "grafana_dashboards" {
  metadata {
    name = "kafka-dashboards"
    labels = {
      "grafana_dashboard" = "1"
    }
  }

  data = {
    "strimzi-cruise-control" = "${templatefile("grafana-dashboards/strimzi-cruise-control.json", { "DS_PROMETHEUS" = "default" })}"
    "strimzi-kafka-bridge"   = "${templatefile("grafana-dashboards/strimzi-kafka-bridge.json", { "DS_PROMETHEUS" = "default" })}"
    "strimzi-kafka-connect"  = "${templatefile("grafana-dashboards/strimzi-kafka-connect.json", { "DS_PROMETHEUS" = "default" })}"
    "strimzi-kafka-exporter" = "${templatefile("grafana-dashboards/strimzi-kafka-exporter.json", { "DS_PROMETHEUS" = "default" })}"
    "strimzi-kafka.json"     = "${templatefile("grafana-dashboards/strimzi-kafka.json", { "DS_PROMETHEUS" = "default" })}"
    "strimzi-operators.json" = "${templatefile("grafana-dashboards/strimzi-operators.json", { "DS_PROMETHEUS" = "default" })}"
    "strimzi-zookeeper.json" = "${templatefile("grafana-dashboards/strimzi-zookeeper.json", { "DS_PROMETHEUS" = "default" })}"
  }
}
