resource "oci_core_vcn" "test_vcn" {
  #Required
  compartment_id = var.compartment_id

  #Optional
  cidr_block = var.vcn_cidr_block
  cidr_blocks = var.vcn_cidr_blocks
  defined_tags = {"Operations.CostCenter"= "42"}
  display_name = var.vcn_display_name
  dns_label = var.vcn_dns_label
  freeform_tags = {"Department"= "Finance"}
  is_ipv6enabled = var.vcn_is_ipv6enabled
}
