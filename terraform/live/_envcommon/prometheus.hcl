terraform {
  source = "../../../../modules/helm//."
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
  }]
}
