# Инфраструктура для UI автотестов (VDS / CI)

Этот репозиторий содержит готовую конфигурацию (Infrastructure as Code) для развертывания безопасного, отказоустойчивого и автоматизированного окружения под браузерные UI тесты.

Основным компонентом является официальный **Selenium Grid 4** в режиме *Dynamic Grid*. Контейнеры с браузерами (Chrome, Firefox, Edge) инициализируются в фоновом режиме исключительно при поступлении запроса на новую сессию и автоматически уничтожаются после её завершения. Это позволяет оптимизировать потребление ресурсов VDS и предотвратить утечки памяти.

---

## Архитектура безопасности

Инфраструктура спроектирована с учетом предотвращения несанкционированного доступа к сервисам Selenium Grid. В конфигурации реализованы следующие защитные механизмы:

- **Traefik Reverse Proxy:** Направляет трафик в изолированную сеть Docker. Ни один контейнер не экспонирует порты во внешнюю сеть хоста напрямую (разрешены только порты 80 и 443).
- **Let's Encrypt:** Traefik автоматически генерирует и продлевает валидные SSL/TLS сертификаты.
- **Basic Auth:** Установлена обязательная аутентификация для доступа к узлу Grid.
- **Инициализация сервера:** Интегрирован bash-скрипт для первичной настройки VDS (отключение root, аутентификации по паролю, конфигурация UFW и Fail2ban).

---

## Инструкция по развертыванию

### Шаг 1. Подготовка сервера и DNS
1. Арендуйте VDS-сервер на базе Ubuntu 22.04 LTS / 24.04 LTS (рекомендуемые характеристики: от 4 vCPU, 8 ГБ RAM в зависимости от степени параллелизма). При создании сервера необходимо внедрить открытый SSH-ключ.
2. Сконфигурируйте A-запись в панели управления DNS, перенаправив целевой домен (например, `selenium.myproject.com`) на публичный IP-адрес сервера.

### Шаг 2. Развертывание (Автоматизированное через CI/CD)

В репозитории реализован GitHub Actions Workflow (`.github/workflows/deploy-grid.yml`) для полностью автоматизированного развертывания и базовой harden-настройки целевой ОС.

1. Сгенерируйте надежный пароль для доступа к Selenium (далее `SELENIUM_PASSWORD`).
2. В параметрах репозитория (`Settings` -> `Secrets and variables` -> `Actions`) создайте следующие секреты:
   - `SSH_PRIVATE_KEY` (приватная часть SSH-ключа, привязанного к серверу).
   - `SELENIUM_PASSWORD` (чистый текстовый пароль).
3. Перейдите в раздел **Actions**, выберите "Deploy Selenium Grid to Clean VDS" и запустите Workflow.
4. Введите необходимые входные данные (IP-адрес, домен, email, логин пользователя). Пайплайн произведет настройку сервера и запустит контейнеры автоматически.

### Шаг 3. Развертывание (Ручное)

Если CI/CD не используется, выполните следующие действия:

1. Скопируйте содержимое директории `selenium_grid_4` на целевой сервер.
2. Создайте файл конфигурации среды:
   ```bash
   cp .env.example .env
   ```
3. Сконфигурируйте значения в `.env`, включая генерацию хеша Basic-авторизации (см. инструкции внутри `.env.example`).
4. Запустите инфраструктуру:
   ```bash
   sudo docker compose up -d
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