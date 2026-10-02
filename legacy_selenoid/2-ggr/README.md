### Создаем рабочую директорию для `ggr`
```dockerfile
sudo mkdir -p /etc/grid-router/quota
```

```shell
sudo apt install apache2-utils -y
```

### Шифруем пользователя паролем с помощью утилиты `htpasswd`
```
sudo htpasswd -bc /etc/grid-router/users.htpasswd it Qwerty123
```
### Создаем файл содержащий список `selenoid` контейнеров
```shell
sudo nano /etc/grid-router/quota/it.xml
```

### Запускаем контейнер `ggr`
```dockerfile
sudo docker run \
--rm \
-d \
--name ggr \
-v /etc/grid-router/:/etc/grid-router:ro \
-p 4444:4444 \
aerokube/ggr:1.7.2 \
-guests-allowed \
-guests-quota "it" \
-verbose \
-quotaDir /etc/grid-router/quota
```

```shell
sudo apt purge apache2-utils -y
```