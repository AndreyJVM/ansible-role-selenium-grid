### Создаем рабочую директорию, у Вас пользователь в `/home` скорее всего отличается
```shell
mkdir /home/it/nginx
```

### Создаем `selenoid.conf` с содержимым proxy сервера
```shell
nano /home/it/nginx/selenoid.conf
```

### Запускаем контейнер `nginx`
```dockerfile
sudo docker run \
--rm \
--name nginx \
-v /home/it/nginx/:/etc/nginx/conf.d:ro \
-d --network=host nginx
```

---

### Если вы все действия проделывали на локальной виртуальной машине не забудьте пробросить порты для каждого контейнера
![port-mapping-with-vm.png](..%2Fimg%2Fport-mapping-with-vm.png)

## Готово, у вас готовое решение современного распараллеливания selenoid
![nginx-proxy.png](..%2Fimg%2Fnginx-proxy.png)