# UI Tests Infrastructure

Репозиторий содержит инфраструктурные скрипты, Ansible-плейбуки и файлы конфигурации для развертывания масштабируемой среды Selenium Grid 4 на Linux VDS.

## Основные компоненты

- **Selenium Grid 4 (Router + Nodes):** Современная реализация Grid, готовая к масштабированию.
- **Traefik (Reverse Proxy):** Обеспечивает маршрутизацию трафика и HTTPS для безопасной работы.
- **Let's Encrypt:** Traefik автоматически генерирует и продлевает валидные SSL/TLS сертификаты.
- **Basic Auth:** Установлена обязательная аутентификация для доступа к узлу Grid.
- **Инициализация сервера (Ansible):** Интегрирован Ansible-playbook для первичной настройки VDS (создание non-root пользователя, установка Docker, UFW-файрвол, защита SSH).

---

## Инструкция по развертыванию

### Шаг 1. Подготовка сервера и DNS
1. Арендуйте VDS-сервер на базе Ubuntu 22.04 LTS / 24.04 LTS или Debian 12 (рекомендуемые характеристики: от 4 vCPU, 8 ГБ RAM в зависимости от степени параллелизма). При создании сервера необходимо внедрить открытый SSH-ключ.
2. Сконфигурируйте A-запись в панели управления DNS, перенаправив целевой домен (например, `selenium.myproject.com`) на публичный IP-адрес сервера.

### Шаг 2. Развертывание (Ansible)

Развертывание производится **только через Ansible** с хоста, с которого копировался открытый SSH-ключ при создании сервера. 
Не используйте CI/CD для деплоя инфраструктуры - пайплайны предназначены только для запуска интеграционных тестов.

1. Запустите Playbook с вашего компьютера (должен быть установлен `ansible`):
   ```bash
   ansible-playbook -i "IP_ВАШЕГО_СЕРВЕРА," -u root ansible/setup_vds.yml
   ```
   Ansible **безопасно запросит** у вас ввод необходимых данных (логин, пароль, домен и email). Введенный пароль не будет отображаться на экране и не сохранится в истории терминала.

   *(Для автоматизированного запуска без интерактивного ввода можно передать переменные через `-e "basic_user=admin basic_password=pass domain_name=... acme_email=..."`)*

   **Готово!** Ansible сам настроит сервер, скопирует все нужные файлы, сгенерирует `.env` файл с хэшем пароля для Basic Auth и поднимет Docker Compose.

---

## Тестирование Инфраструктуры

В проекте реализованы автоматизированные BDD интеграционные тесты с использованием **Java + Testcontainers**. Тесты автоматически поднимают чистые образы ОС (Ubuntu 22.04 и 24.04), прогоняют `ansible-playbook` и валидируют состояние системы (создание пользователей, UFW, Chrony, SSH-ключи).

Для запуска тестов (требуется локально установленный Docker и Maven):
```bash
mvn test
```

---

## Пример интеграции (Java)

> [!WARNING]
> Никогда не коммитьте логины и пароли в исходный код (даже для тестовых контуров).

Секретные данные должны передаваться исключительно через переменные окружения ОС (Environment Variables) на агенте сборки.

Пример инициализации драйвера:

```java
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.RemoteWebDriver;
import java.net.URL;

public class BrowserFactory {
    public static RemoteWebDriver createDriver() throws Exception {
        ChromeOptions options = new ChromeOptions();
        
        String seleniumUser = System.getenv("SELENIUM_USER"); 
        String seleniumPass = System.getenv("SELENIUM_PASS");
        String seleniumHost = System.getenv("SELENIUM_HOST"); 
        
        if (seleniumUser == null || seleniumPass == null || seleniumHost == null) {
            throw new IllegalArgumentException("Grid credentials are not set in environment variables.");
        }
        
        String gridUrl = String.format("https://%s:%s@%s/wd/hub", seleniumUser, seleniumPass, seleniumHost);
        return new RemoteWebDriver(new URL(gridUrl), options);
    }
}
```

> [!NOTE]
> Устаревшая реализация на базе Aerokube Selenoid + GGR перенесена в директорию `legacy_selenoid`. Рекомендуется использовать текущую конфигурацию Grid 4 для всех новых проектов.