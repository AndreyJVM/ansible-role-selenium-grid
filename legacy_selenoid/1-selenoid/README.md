### Для запуска двух selenoid необходимо создать две сети
### Для каждого `seleboid` необходимо создать свою `docker` сеть, в примере поднимается два `selenoid`, соответственно создаём две сети
```dockerfile
sudo docker network create selenoid
```
```dockerfile
sudo docker network create selenoid1
```

### Содержимое для файла находиться в данной папке с названием `browsers.json`
```dockerfile
nano ./browsers.json
```

### Загружаем образы нужных браузеров, эти версии должны быть указаны в `browsers.json`
```dockerfile
sudo docker pull selenoid/chrome:121.0
```

### Проверяем путь из которого копируем `browsers.json`:  -v `/home/it/browsers.json`:/etc/selenoid/browsers.json:ro 
```dockerfile
sudo docker run \
    -d \
    --rm \
    --network selenoid \
    --name selenoid \
    -p 4445:4444 \
    -v /var/run/docker.sock:/var/run/docker.sock \
    -v /home/it/browsers.json:/etc/selenoid/browsers.json:ro \
    aerokube/selenoid:1.11.2 \
    -container-network=selenoid -limit 12
```

```dockerfile
sudo docker run \
    -d \
    --rm \
    --network selenoid1 \
    --name selenoid1 \
    -p 4446:4444 \
    -v /var/run/docker.sock:/var/run/docker.sock \
    -v /home/it/browsers.json:/etc/selenoid/browsers.json:ro \
    aerokube/selenoid:1.11.2 \
    -container-network=selenoid1 -limit 12
```

### Если Вы всё сделали правильно, то в `docker ps` появиться сообщение об успешном старте `selenoid`
