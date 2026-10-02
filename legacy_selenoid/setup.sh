#!/bin/bash
# Автоматическая настройка и подготовка к работе docker compose

### Подготовка selenoid
sudo docker network create selenoid

sudo docker network create selenoid1

nano /home/it/browsers.json << EOF
{
  "chrome": {
    "default": "121.0",
    "versions": {
      "121.0": {
        "image": "selenoid/chrome:121.0",
        "port": "4444",
        "path": "/"
      },
      "120.0": {
        "image": "selenoid/chrome:120.0",
        "port": "4444",
        "path": "/"
      }
    }
  },
  "opera": {
    "default": "106.0",
    "versions": {
      "106.0": {
        "image": "selenoid/opera:106.0",
        "port": "4444",
        "path": "/"
      },
      "105.0": {
        "image": "selenoid/opera:105.0",
        "port": "4444",
        "path": "/"
      }
    }
  }
}
EOF

sudo docker pull selenoid/chrome:121.0

### Подготовка ggr
sudo mkdir -p /etc/grid-router/quota

sudo apt install apache2-utils -y

sudo htpasswd -bc /etc/grid-router/users.htpasswd it Qwerty123

sudo nano /etc/grid-router/quota/it.xml << EOF
<qa:browsers xmlns:qa="urn:config.gridrouter.qatools.ru">
    <browser name="chrome" defaultVersion="121.0">
        <version number="121.0">
            <region name="1">
                <host name="172.18.0.1" port="4445" count="1"/>
                <host name="172.19.0.1" port="4446" count="2"/>
            </region>
        </version>
        <version number="120.0">
            <region>
                <host name="172.18.0.1" port="4445" count="1"/>
                <host name="172.19.0.1" port="4446" count="2"/>
            </region>
        </version>
    </browser>
</qa:browsers>
EOF

sudo apt purge apache2-utils -y

### Подготовка nginx

mkdir /home/it/nginx

nano /home/it/nginx/selenoid.conf << EOF
upstream selenoid_ui {
            random;
            server 127.0.0.1:8080;
        }

upstream ggr {
            random;
            server 127.0.0.1:4444;
        }

server {
    listen 90 default_server;

    location / {
        proxy_pass http://selenoid_ui;
        proxy_http_version 1.1;

        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "Upgrade";
        proxy_set_header Host $http_host;

        proxy_buffering off;
   }

   location ~ /static/js/ {
       proxy_pass http://selenoid_ui;
   }

   location ~ /static/css/ {
       proxy_pass http://selenoid_ui;
   }
   location ~ /static/media/ {
       proxy_pass http://selenoid_ui;
   }

   location /status {
       proxy_pass http://selenoid_ui;
   }

   location /events {
       proxy_pass http://selenoid_ui;
   }

   location ~ ^/vnc/ {
        proxy_pass http://selenoid_ui;
        proxy_http_version 1.1;

        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "Upgrade";
        proxy_set_header Host $http_host;
   }

   location ~ ^/logs/ {
        proxy_pass http://selenoid_ui;
        proxy_http_version 1.1;

        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "Upgrade";
        proxy_set_header Host $http_host;
   }

   location ~ /wd/hub/ {
      proxy_pass http://ggr;
   }
}
EOF



