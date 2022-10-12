
resource "kubectl_manifest" "app" {
  yaml_body = templatefile("app-manifests/app.yaml", {
    "APP_IMAGE" = var.app_image})
}
