# Selenium Grid 4 Secure Deployment Role

[![Ansible Galaxy](https://img.shields.io/badge/Ansible%20Galaxy-AndreyJVM.selenium__grid__secure-blue)](https://galaxy.ansible.com/AndreyJVM/selenium_grid_secure)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

> [!IMPORTANT]
> **Security First.** Many repositories offer quick scripts to deploy Selenium Grid. However, if real security matters to you (closed ports, no root access, mandatory Basic Auth, and automated Let's Encrypt certificates for HTTPS) — **you are in the right place**. The entire infrastructure is deployed automatically, leaving no backdoors for bots or cryptominers.

An Ansible Role that automates the deployment of a highly scalable **Selenium Grid 4** environment on a fresh Linux VDS. It configures the server, secures it, installs Docker, and launches the Grid behind a **Traefik Reverse Proxy**.

## Table of Contents
- [Features](#features)
- [Requirements](#requirements)
- [Quick Start (Copy-Paste)](#quick-start-copy-paste)
- [Advanced Configuration](#advanced-configuration)
- [Updating the Grid](#updating-the-grid)
- [Integration with Java (WebDriver Factory)](#integration-with-java-webdriver-factory)

## Features

- **Selenium Grid 4 (Router + Nodes):** Modern implementation of Selenium Grid, ready for scaling.
- **Traefik (Reverse Proxy):** Provides routing and HTTPS for secure operation.
- **Let's Encrypt:** Traefik automatically generates and renews valid SSL/TLS certificates.
- **Basic Auth:** Mandatory authentication for accessing the Grid nodes.
- **Server Initialization:** Automatically sets up the VDS (creates a non-root `deployer` user, installs Docker, configures UFW firewall, secures SSH by disabling root login and password auth, generates `htpasswd`, and deploys the Grid).

## Requirements

Before running the playbook, ensure your server meets the following conditions:
- [x] **Clean OS:** Fresh installation of Ubuntu 22.04 LTS, Ubuntu 24.04 LTS, or Debian 12. No prior setup (Docker, Nginx, etc.) is required.
- [x] **SSH Access:** Your public SSH key is added to `/root/.ssh/authorized_keys`. You must be able to SSH into the server as `root` without a password from your local terminal.
- [x] **Configured DNS:** An `A` record points to the public IP address of this VDS (e.g., `selenium.my-domain.com`). *Wait for DNS propagation, otherwise Let's Encrypt will fail to issue the certificate!*
- [x] **Hardware:** Minimum 4 vCPU and 8 GB RAM (to comfortably run the hub and 3-4 parallel browser sessions).

## Quick Start (Copy-Paste)

This guide is designed for QA Automation Engineers. You don't need advanced DevOps skills! Just open your local Linux/macOS terminal and copy-paste the blocks below.

**1. Install Ansible (if you don't have it on your local machine)**
```bash
sudo apt update && sudo apt install -y ansible
```

**2. Download the role from Ansible Galaxy**
```bash
ansible-galaxy install AndreyJVM.selenium_grid_secure
```

**3. Create the deployment file**
Copy this entire block and paste it into your terminal, then press Enter. It will create a `deploy-grid.yml` file automatically:
```bash
cat << 'EOF' > deploy-grid.yml
---
- hosts: all
  become: yes
  vars_prompt:
    - name: basic_user
      prompt: "Enter Basic Auth Username for Selenium Grid"
      default: "admin"
      private: no
    - name: basic_password
      prompt: "Enter Basic Auth Password"
      private: yes
    - name: domain_name
      prompt: "Enter Domain Name (e.g. selenium.my-domain.com)"
      private: no
    - name: acme_email
      prompt: "Enter Email for Let's Encrypt SSL"
      private: no
  roles:
    - AndreyJVM.selenium_grid_secure
EOF
```

**4. Run the deployment**
Replace `192.168.1.100` with the actual IP address of your new server:
```bash
ansible-playbook -i "192.168.1.100," -u root deploy-grid.yml
```
*(⚠️ Note: Do not forget the comma `,` after the IP address!)*

**Done!** The script will ask you for the domain, email, and credentials, and then do all the magic. 

## Advanced Configuration

If you want to customize the deployment, you can add variables to your `deploy-grid.yml` file under a `vars:` section:

```yaml
  vars:
    selenium_grid_project_path: "/opt/my-custom-path" # Default is /opt/ui-tests-infra
    selenium_grid_http_port: "8080"                   # Default is 80
    selenium_grid_https_port: "8443"                  # Default is 443
    selenium_grid_version: "4.20.0"                   # Override Selenium version
    traefik_version: "v3.6"                           # Override Traefik version
```

## Updating the Grid

If you manually modify the configuration files inside the role (e.g., adding new browser nodes to `docker-compose.yml`), you do not need to run the full server initialization again.

Use the `update` tag to quickly push configuration changes and restart the containers. Since `root` login is automatically disabled after the first run for security reasons, execute this as the `deployer` user:

```bash
ansible-playbook -i "192.168.1.100," -u deployer deploy-grid.yml --tags update
```

## Integration with Java (WebDriver Factory)

You do not need to copy this repository into your test automation projects. This infrastructure lives independently. In your Java project, simply implement a Factory pattern to manage your WebDriver.

Example `WebDriverFactory.java`:

```java
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.RemoteWebDriver;
import java.net.URI;

public class WebDriverFactory {
    public static WebDriver createDriver() {
        // Control execution via RUN_ON_GRID property
        boolean runOnGrid = Boolean.parseBoolean(System.getProperty("RUN_ON_GRID", "false"));

        if (runOnGrid) {
            // Connect to your secured infrastructure on the VDS
            ChromeOptions options = new ChromeOptions();
            String gridUrl = String.format("https://%s:%s@%s/wd/hub", 
                    System.getenv("GRID_USER"), 
                    System.getenv("GRID_PASS"), 
                    System.getenv("GRID_DOMAIN"));
            try {
                return new RemoteWebDriver(URI.create(gridUrl).toURL(), options);
            } catch (Exception e) { 
                throw new RuntimeException("Failed to connect to Remote Grid", e); 
            }
        } else {
            // Local execution for debugging
            return new ChromeDriver(); 
        }
    }
}
```

Run tests locally:
```bash
mvn test
```

Run tests in CI/CD pipeline on the remote server:
```bash
mvn test -DRUN_ON_GRID=true
```
