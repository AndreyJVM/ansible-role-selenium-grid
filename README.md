# Selenium Grid 4 Secure Deployment Role

[![Ansible Galaxy](https://img.shields.io/badge/Ansible%20Galaxy-AndreyJVM.selenium__grid__secure-blue)](https://galaxy.ansible.com/AndreyJVM/selenium_grid_secure)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

> [!IMPORTANT]
> **Security First.** Many repositories offer quick scripts to deploy Selenium Grid. However, if real security matters to you (closed ports, no root access, mandatory Basic Auth, and automated Let's Encrypt certificates for HTTPS) — **you are in the right place**. The entire infrastructure is deployed automatically, leaving no backdoors for bots or cryptominers.

An Ansible Role that automates the deployment of a highly scalable **Selenium Grid 4** environment on a fresh Linux VDS. It configures the server, secures it, installs Docker, and launches the Grid behind a **Traefik Reverse Proxy**.

## Table of Contents
- [Features](#features)
- [Requirements](#requirements)
- [Usage (Ansible Galaxy)](#usage-ansible-galaxy)
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
- [x] **SSH Access:** Your public SSH key is added to `/root/.ssh/authorized_keys`. You must be able to SSH into the server as `root` without a password.
- [x] **Configured DNS:** An `A` record points to the public IP address of this VDS (e.g., `selenium.my-domain.com`). *Wait for DNS propagation, otherwise Let's Encrypt will fail to issue the certificate!*
- [x] **Hardware:** Minimum 4 vCPU and 8 GB RAM (to comfortably run the hub and 3-4 parallel browser sessions).

## Usage (Ansible Galaxy)

Install the role from Ansible Galaxy:
```bash
ansible-galaxy install AndreyJVM.selenium_grid_secure
```

Create a `playbook.yml` file on your local machine:
```yaml
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
```

Run the playbook:
```bash
ansible-playbook -i "YOUR_SERVER_IP," -u root playbook.yml
```
**Done!** Your server is configured, secured with a firewall, and running a secure Selenium Grid.

## Updating the Grid

If you manually modify the configuration files inside the role (e.g., adding new browser nodes to `docker-compose.yml`), you do not need to run the full server initialization again.

Use the `update` tag to quickly push configuration changes and restart the containers. Since `root` login is disabled after the first run, execute this as the `deployer` user:

```bash
ansible-playbook -i "YOUR_SERVER_IP," -u deployer playbook.yml --tags update
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