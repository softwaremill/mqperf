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
    replicas: 3
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
        deleteClaim: false
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
    replicas: 3
    storage:
      type: persistent-claim
      size: 20Gi
      deleteClaim: true
  entityOperator:
    topicOperator: {}
    userOperator: {}
YAML


  depends_on = [
    module.helm.release_name
  ]
}
