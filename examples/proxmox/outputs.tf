output "vm_ip" {
  description = "Static IP of the provisioned Proxmox VM"
  value       = local.clean_ip
}

output "ssh_command" {
  description = "SSH connection command"
  value       = "ssh -i ${pathexpand(replace(var.ssh_public_key_path, ".pub", ""))} ${var.vm_user}@${local.clean_ip}"
}

output "ansible_quick_run" {
  description = "Ready-to-run Ansible Playbook command"
  value       = "ansible-playbook -i \"${local.clean_ip},\" -u ${var.vm_user} deploy-grid.yml"
}
