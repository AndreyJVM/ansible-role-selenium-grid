terraform {
  required_version = ">= 1.5.0"

  required_providers {
    selectel = {
      source  = "selectel/selectel"
      version = "~> 6.0"
    }
    openstack = {
      source  = "terraform-provider-openstack/openstack"
      version = "~> 2.1"
    }
    local = {
      source  = "hashicorp/local"
      version = "~> 2.5"
    }
  }
}

# Provider configuration using Selectel project service user credentials or account credentials
provider "openstack" {
  auth_url    = var.auth_url
  domain_name = var.selectel_account_id
  tenant_id   = var.selectel_project_id
  user_name   = var.selectel_user_name
  password    = var.selectel_password
  region      = var.region
}
