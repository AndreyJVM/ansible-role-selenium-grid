resource "proxmox_virtual_environment_vm" "selenium_grid" {
  node_name = var.proxmox_node
  vm_id     = var.vm_id
  name      = var.vm_name

  clone {
    vm_id = var.template_vm_id
    full  = true
  }

  cpu {
    cores = var.vm_cores
    type  = "host"
  }

  memory {
    dedicated = var.vm_memory_mb
  }

  disk {
    datastore_id = var.vm_datastore_id
    size         = var.vm_disk_size_gb
    interface    = "scsi0"
  }

  network_device {
    bridge = var.vm_bridge
    model  = "virtio"
  }

  initialization {
    ip_config {
      ipv4 {
        address = var.vm_ip_cidr
        gateway = var.vm_gateway
      }
    }

    dns {
      servers = var.vm_dns_servers
    }

    user_account {
      username = var.vm_user
      keys     = [trimspace(file(pathexpand(var.ssh_public_key_path)))]
    }
  }

  agent {
    enabled = true
  }
}

locals {
  clean_ip = split("/", var.vm_ip_cidr)[0]
}

resource "local_file" "ansible_inventory" {
  content = templatefile("${path.module}/inventory.tpl", {
    server_ip    = local.clean_ip
    ssh_user     = var.vm_user
    ssh_key_path = pathexpand(replace(var.ssh_public_key_path, ".pub", ""))
  })
  filename = "${path.module}/hosts.ini"
}
