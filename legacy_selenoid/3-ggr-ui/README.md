### Запускаем ggr-ui

```dockerfile
sudo docker run \
--rm \
-d \
--name ggr-ui \
-p 8888:8888 \
-v /etc/grid-router/quota/:/etc/grid-router/quota:ro \
aerokube/ggr-ui:1.2.0
```

### Проверяем доступность браузеров
```shell
curl -s http://localhost:8888/status
```

### Успешно
```shell
# Пример ответа
{"browsers":{"chrome":{"120.0":{},"121.0":{}},"opera":{"105.0":{},"106.0":{}}},"pending":0,"queued":0,"total":24,"used":0}
```

### Где-то ошибка
```shell
# Пустой json массив 
{}
```