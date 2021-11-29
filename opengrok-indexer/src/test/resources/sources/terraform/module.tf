module "servers" {
  source = "./app-cluster"

  servers = 5
}
