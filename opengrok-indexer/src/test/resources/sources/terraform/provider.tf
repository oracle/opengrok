provider "oci" {
  auth = "InstancePrincipal"
  region = "${var.region}"
}
