locals {
  storage_classes_names = [for sc in toset(var.eks_storage_classes) : sc.name]
  storage_classes       = zipmap(local.storage_classes_names, tolist(toset(var.eks_storage_classes)))
}
