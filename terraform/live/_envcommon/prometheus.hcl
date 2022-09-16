terraform {
  source = "../../../../modules/helm//."

  before_hook "select workspace" {
    commands = ["plan", "state", "apply", "destroy", "refresh"]
    execute  = ["${dirname(find_in_parent_folders())}/../../../workspace-hook.sh", get_env("CLUSTER_NAME")]
  }
}

inputs = {
  release_name  = "kube-prometheus-stack"
  repository    = "https://prometheus-community.github.io/helm-charts"
  chart_name    = "kube-prometheus-stack"
  chart_version = "39.6.0"
  sets = [
    {
      name  = "prometheus.prometheusSpec.podMonitorSelectorNilUsesHelmValues"
      value = "false"
    },
    {
      name  = "prometheus.prometheusSpec.serviceMonitorSelectorNilUsesHelmValues"
      value = "false"
    },
    {
      name  = "prometheus.prometheusSpec.nodeSelector.node-group"
      value = "controllers"
    },
    {
      name  = "alertmanager.alertmanagerSpec.nodeSelector.node-group"
      value = "controllers"
    },
    {
      name  = "prometheusOperator.nodeSelector.node-group"
      value = "controllers"
    },
    {
      name  = "kube-state-metrics.nodeSelector.node-group"
      value = "controllers"
    },
    {
      name  = "grafana.nodeSelector.node-group"
      value = "controllers"
    }
  ]
}
