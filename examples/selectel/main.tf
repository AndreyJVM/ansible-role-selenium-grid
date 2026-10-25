data "openstack_networking_network_v2" "external_net" {
  external = true
}

resource "openstack_compute_keypair_v2" "keypair" {
  name       = "${var.server_name}-key"
  public_key = file(pathexpand(var.ssh_public_key_path))
}

resource "openstack_networking_network_v2" "network" {
  name           = "${var.server_name}-net"
  admin_state_up = true
}

resource "openstack_networking_subnet_v2" "subnet" {
  name            = "${var.server_name}-subnet"
  network_id      = openstack_networking_network_v2.network.id
  cidr            = var.server_network_cidr
  ip_version      = 4
  dns_nameservers = ["188.93.16.19", "188.93.17.19"]
}

resource "openstack_networking_router_v2" "router" {
  name                = "${var.server_name}-router"
  admin_state_up      = true
  external_network_id = data.openstack_networking_network_v2.external_net.id
}

resource "openstack_networking_router_interface_v2" "router_interface" {
  router_id = openstack_networking_router_v2.router.id
  subnet_id = openstack_networking_subnet_v2.subnet.id
}

data "openstack_images_image_v2" "os_image" {
  name        = var.image_name
  most_recent = true
}

data "openstack_compute_flavor_v2" "flavor" {
  name = var.flavor_name
}

resource "openstack_blockstorage_volume_v3" "root_volume" {
  name              = "${var.server_name}-root"
  size              = var.volume_size
  image_id          = data.openstack_images_image_v2.os_image.id
  volume_type       = var.volume_type
  availability_zone = var.region
}

resource "openstack_compute_instance_v2" "selenium_server" {
  name              = var.server_name
  flavor_id         = data.openstack_compute_flavor_v2.flavor.id
  key_pair          = openstack_compute_keypair_v2.keypair.name
  availability_zone = var.region

  block_device {
    uuid                  = openstack_blockstorage_volume_v3.root_volume.id
    source_type           = "volume"
    boot_index            = 0
    destination_type      = "volume"
    delete_on_termination = true
  }

  network {
    uuid = openstack_networking_network_v2.network.id
  }

  depends_on = [
    openstack_networking_router_interface_v2.router_interface
  ]
}

resource "openstack_networking_floatingip_v2" "fip" {
  pool = data.openstack_networking_network_v2.external_net.name
}

resource "openstack_compute_floatingip_associate_v2" "fip_assoc" {
  floating_ip = openstack_networking_floatingip_v2.fip.address
  instance_id = openstack_compute_instance_v2.selenium_server.id
}

resource "local_file" "ansible_inventory" {
  content = templatefile("${path.module}/inventory.tpl", {
    server_ip    = openstack_networking_floatingip_v2.fip.address
    ssh_key_path = pathexpand(replace(var.ssh_public_key_path, ".pub", ""))
  })
  filename = "${path.module}/hosts.ini"
}
