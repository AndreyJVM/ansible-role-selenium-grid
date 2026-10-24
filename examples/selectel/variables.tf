variable "selectel_account_id" {
  type        = string
  description = "Selectel Account ID / Domain Name (e.g. '123456')"
}

variable "selectel_project_id" {
  type        = string
  description = "UUID of the Selectel VPC Project (from Cloud Platform panel)"
}

variable "selectel_user_name" {
  type        = string
  description = "Selectel Service User name or account username"
}

variable "selectel_password" {
  type        = string
  description = "Password for the Selectel user"
  sensitive   = true
}

variable "auth_url" {
  type        = string
  description = "Keystone authentication endpoint for Selectel Cloud"
  default     = "https://cloud.api.selcloud.ru/identity/v3"
}

variable "region" {
  type        = string
  description = "Selectel Cloud Region and Pool (e.g. ru-9a for SPb, ru-7a for Moscow)"
  default     = "ru-9a"
}

variable "server_name" {
  type        = string
  description = "Hostname for the Selenium Grid VM"
  default     = "selenium-grid-node"
}

variable "flavor_name" {
  type        = string
  description = "Server flavor (e.g. c4m8-d0 for 4 vCPU, 8 GB RAM - required for parallel test runs)"
  default     = "c4m8-d0"
}

variable "image_name" {
  type        = string
  description = "OS Image name"
  default     = "Ubuntu 24.04 LTS 64-bit"
}

variable "volume_size" {
  type        = number
  description = "Root disk volume size in GB"
  default     = 30
}

variable "volume_type" {
  type        = string
  description = "Volume storage tier in the selected region (e.g. fast.ru-9a, universal.ru-9a)"
  default     = "fast.ru-9a"
}

variable "ssh_public_key_path" {
  type        = string
  description = "Path to your local SSH public key"
  default     = "~/.ssh/id_ed25519.pub"
}

variable "server_network_cidr" {
  type        = string
  description = "Private VPC subnet CIDR"
  default     = "192.168.10.0/24"
}
