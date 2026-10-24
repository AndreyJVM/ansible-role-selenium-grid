# Provisioning Selenium Grid VDS on Selectel Cloud with Terraform

This example automates the provisioning of a high-performance Linux VDS in **Selectel Cloud Platform (VPC)** using Terraform and OpenStack providers, tailored specifically for running this Selenium Grid 4 Ansible role.

## Architecture

Terraform provisions:
- **Private VPC Network & Subnet** with Selectel internal DNS resolvers.
- **VPC Router** connected to the public external network.
- **Fast NVMe Boot Disk (30 GB)** with Ubuntu 24.04 LTS.
- **Compute Instance** with 4 vCPU and 8 GB RAM (`c4m8-d0` flavor), optimal for multi-browser parallel execution.
- **Public Floating IP** mapped directly to the server.
- **SSH Keypair** injected from your local public key.
- **Ansible Inventory (`hosts.ini`)** automatically generated upon deployment completion.

---

## Prerequisites

1. **Selectel Account:** Register at [selectel.ru](https://selectel.ru).
2. **VPC Project & Service User:**
   - In the Selectel Control Panel, go to **Cloud Platform** → **Projects** and create a project (or use an existing one).
   - In **Identity & Access Management (IAM)**, create a service user (e.g. `terraform-user`), assign it the `admin` or `member` role in the project, and note its password.
3. **Terraform CLI:** Install Terraform (`>= 1.5.0`):
   ```bash
   sudo apt install -y terraform
   ```
4. **SSH Key:** Ensure you have an SSH key generated locally (`~/.ssh/id_ed25519.pub`).

---

## Quick Start

### 1. Configure Variables

Copy the example variables file:
```bash
cp terraform.tfvars.example terraform.tfvars
```

Edit `terraform.tfvars` with your credentials:
```hcl
selectel_account_id = "123456"                       # Your Selectel account number
selectel_project_id = "your-project-uuid-here"       # Project UUID
selectel_user_name  = "terraform-user"
selectel_password   = "your-secret-password"
region              = "ru-9a"                        # SPb (ru-9a) or Moscow (ru-7a)
volume_type         = "fast.ru-9a"
ssh_public_key_path = "~/.ssh/id_ed25519.pub"
```

### 2. Initialize and Apply

```bash
terraform init
terraform plan
terraform apply
```

Upon successful completion, Terraform outputs the public IP and generates `hosts.ini`:
```text
Outputs:
server_public_ip = "185.123.45.67"
ssh_command = "ssh -i ~/.ssh/id_ed25519 root@185.123.45.67"
ansible_quick_run = "ansible-playbook -i \"185.123.45.67,\" -u root deploy-grid.yml"
```

### 3. Deploy Selenium Grid via Ansible

Make sure your DNS `A` record (e.g. `selenium.my-domain.com`) points to `server_public_ip`. Then execute the role from the root of this repository:

```bash
# Using the auto-generated hosts.ini:
ansible-playbook -i examples/selectel/hosts.ini deploy-grid.yml

# Or using the inline IP:
ansible-playbook -i "185.123.45.67," -u root deploy-grid.yml
```

---

## Clean Up

To destroy all cloud resources and stop billing:
```bash
terraform destroy
```
