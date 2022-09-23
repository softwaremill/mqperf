resource "kubernetes_storage_class" "storage_class" {

  for_each = local.storage_classes

  metadata {
    name = lookup(each.value, "name", "")
  }

  storage_provisioner = lookup(each.value, "storage_class_provisioner", "")

  parameters = each.value.parameters

  volume_binding_mode = lookup(each.value, "volume_binding_mode", "WaitForFirstConsumer")
  reclaim_policy      = lookup(each.value, "reclaim_policy", "Delete")
}

resource "aws_iam_role" "storageclass_role" {
  name               = "AmazonEKS_EBS_CSI_DriverRole_${var.cluster_name}"
  assume_role_policy = <<EOF
  {
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Principal": {
        "Federated": "arn:aws:iam::${var.aws_account_id}:oidc-provider/${var.oidc_provider_url}"
      },
      "Action": "sts:AssumeRoleWithWebIdentity",
      "Condition": {
        "StringEquals": {
          "${var.oidc_provider_url}:aud": "sts.amazonaws.com",
          "${var.oidc_provider_url}:sub": "system:serviceaccount:kube-system:ebs-csi-controller-sa"
        }
      }
    }
  ]
}
EOF
}

resource "aws_iam_role_policy_attachment" "AmazonEBSCSIDriverPolicy" {
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonEBSCSIDriverPolicy"
  role       = aws_iam_role.storageclass_role.name
}

