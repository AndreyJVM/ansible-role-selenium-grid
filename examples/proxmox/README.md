# Provisioning Selenium Grid VM on Proxmox VE with Terraform

This module automates the provisioning of a dedicated **Selenium Grid 4** virtual machine on **Proxmox VE 7 / 8** using the modern [`bpg/proxmox`](https://registry.terraform.io/providers/bpg/proxmox/latest) Terraform provider.

## Architecture

Terraform provisions:
- Full clone of an existing Ubuntu Cloud-Init template.
- Hardware configuration: 4 vCPU (`host` type) and 8 GB RAM.
- 30 GB storage on your chosen datastore (e.g. `local-lvm`).
- Cloud-Init configuration: static IP, gateway, DNS nameservers, and SSH public key injection.
- QEMU Guest Agent enablement.
- Automatic generation of Ansible inventory ([`hosts.ini`](hosts.ini)) for 1-click role deployment.

---

## Prerequisites

1. **Proxmox VE:** Proxmox VE 7.x or 8.x with an accessible API (`https://pve.example.lan:8006/`).
2. **Cloud-Init Template:** A pre-created Ubuntu 22.04 or 24.04 Cloud-Init VM template (e.g., VM ID `9000`) with QEMU Guest Agent installed.
3. **Proxmox API Token:**
   - In PVE web UI: **Datacenter** → **Permissions** → **API Tokens** → **Add**.
   - Create a token (e.g. `ansible@pve!selenium`) with `PVEVMAdmin` or `Administrator` privileges.
4. **Terraform CLI:** Install Terraform (`>= 1.5.0`):
   ```bash
   sudo apt install -y terraform
   ```
5. **DNS Record:** Ensure your domain (e.g. `selenium.my-domain.com`) points to the IP reserved for this VM so Let's Encrypt can issue TLS certificates.

---

## Quick Start

### 1. Configure Variables

```bash
cd examples/proxmox
cp terraform.tfvars.example terraform.tfvars
```

Edit `terraform.tfvars` with your Proxmox API credentials and network settings:
```hcl
proxmox_endpoint  = "https://pve.example.lan:8006/"
proxmox_api_token = "USER@REALM!TOKENID=UUID"
proxmox_insecure  = true # Set false if using trusted certificates
proxmox_node      = "pve"

template_vm_id    = 9000
vm_id             = 901
vm_ip_cidr        = "192.168.1.90/24"
vm_gateway        = "192.168.1.1"
ssh_public_key_path = "~/.ssh/id_ed25519.pub"
```

### 2. Initialize and Apply

```bash
terraform init
terraform plan
terraform apply
```

Upon completion, Terraform starts the VM, waits for initialization, and outputs:
```text
Outputs:
vm_ip = "192.168.1.90"
ssh_command = "ssh -i ~/.ssh/id_ed25519 root@192.168.1.90"
ansible_quick_run = "ansible-playbook -i \"192.168.1.90,\" -u root deploy-grid.yml"
```

### 3. Deploy Selenium Grid via Ansible

Deploy the secure Selenium Grid role from the repository root:

```bash
# Using the auto-generated hosts.ini:
ansible-playbook -i examples/proxmox/hosts.ini deploy-grid.yml

# Or using the inline IP:
ansible-playbook -i "192.168.1.90," -u root deploy-grid.yml
```

---

## Clean Up

To remove the VM from Proxmox and release storage:
```bash
terraform destroy
```
