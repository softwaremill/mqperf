remote_state {
  backend = "gcs"
  generate = {
    path      = "backend.tf"
    if_exists = "overwrite"
  }
  config = {
    project  = "${get_env("TF_VAR_GCP_PROJECT")}"
    location = "${get_env("TF_VAR_GCS_LOCATION", "eu")}"
    bucket   = "${get_env("TF_VAR_BUCKET_NAME")}"
    prefix   = "${path_relative_to_include()}/terraform.tfstate"

    gcs_bucket_labels = {
      owner = "terraform"
      name  = "mqperf_state_storage"
    }
  }
}

generate "provider" {
  path      = "provider.tf"
  if_exists = "skip"
  contents  = <<EOF
terraform {
  required_providers {
    google = {
      source = "hashicorp/google"
      version = "4.33.0"
    }
  }
}

provider "google" {
  project     = "${get_env("TF_VAR_GCP_PROJECT")}"
  region      = "${get_env("TF_VAR_GCP_REGION", "europe-central2")}"
}
EOF
}
