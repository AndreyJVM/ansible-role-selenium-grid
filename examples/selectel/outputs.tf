output "server_public_ip" {
  description = "Public Floating IP of the provisioned Selenium Grid VDS"
  value       = openstack_networking_floatingip_v2.fip.address
}

output "ssh_command" {
  description = "Command to connect to the server via SSH"
  value       = "ssh -i ${pathexpand(replace(var.ssh_public_key_path, ".pub", ""))} root@${openstack_networking_floatingip_v2.fip.address}"
}

output "ansible_quick_run" {
  description = "Ready-to-run Ansible Playbook command"
  value       = "ansible-playbook -i \"${openstack_networking_floatingip_v2.fip.address},\" -u root deploy-grid.yml"
}
