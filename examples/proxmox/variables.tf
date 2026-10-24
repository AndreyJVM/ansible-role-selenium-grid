variable "proxmox_endpoint" {
  type        = string
  description = "Proxmox VE API endpoint URL (e.g. https://pve.example.lan:8006/)"
}

variable "proxmox_api_token" {
  type        = string
  description = "Proxmox API Token in the format USER@REALM!TOKENID=UUID"
  sensitive   = true
}

variable "proxmox_insecure" {
  type        = bool
  description = "Disable TLS verification for self-signed certificates"
  default     = true
}

variable "proxmox_node" {
  type        = string
  description = "Target Proxmox node name"
  default     = "pve"
}

variable "template_vm_id" {
  type        = number
  description = "VM ID of the existing Ubuntu Cloud-Init template to clone"
  default     = 9000
}

variable "vm_id" {
  type        = number
  description = "VM ID for the new Selenium Grid VM"
  default     = 901
}

variable "vm_name" {
  type        = string
  description = "Hostname and display name for the VM"
  default     = "selenium-grid-01"
}

variable "vm_cores" {
  type        = number
  description = "Number of CPU cores"
  default     = 4
}

variable "vm_memory_mb" {
  type        = number
  description = "Allocated RAM in Megabytes (8192 MB recommended for 3-4 parallel browsers)"
  default     = 8192
}

variable "vm_datastore_id" {
  type        = string
  description = "Storage datastore ID for the VM disk"
  default     = "local-lvm"
}

variable "vm_disk_size_gb" {
  type        = number
  description = "Root disk size in GB"
  default     = 30
}

variable "vm_bridge" {
  type        = string
  description = "Network bridge for the VM"
  default     = "vmbr0"
}

variable "vm_ip_cidr" {
  type        = string
  description = "Static IP address with subnet CIDR (e.g. 192.168.1.90/24)"
}

variable "vm_gateway" {
  type        = string
  description = "Default gateway IPv4 address"
}

variable "vm_dns_servers" {
  type        = list(string)
  description = "List of DNS nameservers"
  default     = ["1.1.1.1", "8.8.8.8"]
}

variable "vm_user" {
  type        = string
  description = "Cloud-Init initial user to create/configure"
  default     = "root"
}

variable "ssh_public_key_path" {
  type        = string
  description = "Path to the local public SSH key file"
  default     = "~/.ssh/id_ed25519.pub"
}
