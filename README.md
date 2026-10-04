# UI Tests Infrastructure

> [!IMPORTANT]
> **Безопасность превыше всего.** Многие репозитории предлагают быстрые скрипты для развертывания Selenium Grid. Но если вам важна реальная безопасность (закрытые порты, отсутствие root-доступа, обязательная Basic Auth, и автоматические сертификаты Let's Encrypt для HTTPS) — **вы по адресу**. Вся инфраструктура разворачивается автоматически, не оставляя дыр для ботов и майнеров.

Репозиторий содержит инфраструктурные скрипты, Ansible-плейбуки и файлы конфигурации для развертывания масштабируемой среды Selenium Grid 4 на Linux VDS.


## Оглавление
- [Основные компоненты](#основные-компоненты)
- [Интеграция в ваши проекты (WebDriver Factory)](#интеграция-в-ваши-проекты-webdriver-factory)
- [Инструкция по развертыванию сервера](#инструкция-по-развертыванию-сервера)
  - [Шаг 1. Требования к серверу перед запуском](#шаг-1-требования-к-серверу-перед-запуском)
  - [Шаг 2. Развертывание (Ansible)](#шаг-2-развертывание-ansible)
- [Обновление инфраструктуры](#обновление-инфраструктуры)
- [Запуск автотестов](#запуск-автотестов)

## Основные компоненты

- **Selenium Grid 4 (Router + Nodes):** Современная реализация Grid, готовая к масштабированию.
- **Traefik (Reverse Proxy):** Обеспечивает маршрутизацию трафика и HTTPS для безопасной работы.
- **Let's Encrypt:** Traefik автоматически генерирует и продлевает валидные SSL/TLS сертификаты.
- **Basic Auth:** Установлена обязательная аутентификация для доступа к узлу Grid.
- **Инициализация сервера (Ansible):** Интегрирован Ansible-playbook для первичной настройки VDS (создание non-root пользователя, установка Docker, UFW-файрвол, защита SSH, генерация `htpasswd` и деплой Grid).

---

## Инструкция по развертыванию сервера

### Шаг 1. Требования к серверу перед запуском

Перед запуском Ansible-плейбука убедитесь, что ваш сервер соответствует следующим условиям:
- [x] **Чистая ОС:** Установлена свежая версия Ubuntu 22.04 LTS, Ubuntu 24.04 LTS или Debian 12. Никакой предварительной настройки (Docker, Nginx) не требуется.
- [x] **SSH-доступ:** На сервер добавлен ваш публичный SSH-ключ (в `/root/.ssh/authorized_keys`). Вы можете зайти на сервер по команде `ssh root@IP_СЕРВЕРА` без пароля.
- [x] **Настроенный DNS:** В панели управления вашим доменом создана `A`-запись, которая указывает (например, `selenium.my-domain.com`) на публичный IP-адрес этого VDS. *Дождитесь обновления DNS, иначе Let's Encrypt не сможет выпустить сертификат!*
- [x] **Аппаратные ресурсы:** Минимум 4 vCPU и 8 ГБ RAM (для запуска хаба и 3-4 параллельных браузеров).

### Шаг 2. Использование Ansible Role

Установите роль из Ansible Galaxy:
```bash
ansible-galaxy install AndreyJVM.selenium_grid_secure
```

Создайте у себя файл `playbook.yml`:
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

Запустите установку:
```bash
ansible-playbook -i "IP_ВАШЕГО_СЕРВЕРА," -u root playbook.yml
```
**Готово!** Сервер настроен, закрыт файрволом, и на нём поднят защищенный Selenium Grid.

---

## Обновление инфраструктуры

Для быстрого обновления конфигурации (без переустановки системных пакетов) используйте тег `update` или запускайте роль напрямую:

```bash
ansible-playbook -i "IP_ВАШЕГО_СЕРВЕРА," -u deployer playbook.yml --tags update
```

---


## Интеграция в ваши проекты (WebDriver Factory)

Вам не нужно копировать этот инфраструктурный проект в свои репозитории с автотестами. Инфраструктура живет своей жизнью, а в вашем проекте на Java (с использованием Selenium или Selenide) достаточно реализовать паттерн Фабрики для управления WebDriver.

Пример `WebDriverFactory` на Java:

```java
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.RemoteWebDriver;
import java.net.URI;

public class WebDriverFactory {
    public static WebDriver createDriver() {
        // Управляем запуском через переменную RUN_ON_GRID
        boolean runOnGrid = Boolean.parseBoolean(System.getProperty("RUN_ON_GRID", "false"));

        if (runOnGrid) {
            // Подключаемся к вашей защищенной инфраструктуре на VDS
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
            // Локальный запуск на компьютере разработчика (дебаг)
            return new ChromeDriver(); 
        }
    }
}
```

Запуск тестов локально:
```bash
mvn test
```

Запуск тестов в CI/CD контуре на удаленном сервере:
```bash
mvn test -DRUN_ON_GRID=true
```

## Запуск автотестов

В проекте реализованы 2 вида проверок:

### 1. Инфраструктурные тесты (Local Testcontainers)
Проверяют работоспособность самого Ansible-плейбука на чистых образах Ubuntu 22.04 и 24.04 внутри локального Docker. Запускаются автоматически в GitHub Actions при каждом пуше.

Для локального запуска (требуется локальный Docker и Maven):
```bash
mvn test -Dtest="infra.*"
```

### 2. UI-тесты на удаленном сервере (RemoteGridTest)
Демонстрируют подключение к реальному Selenium Grid серверу с использованием Basic Auth. 

**Локальный запуск (безопасно):**
Создайте в корне проекта файл `.env.properties` (он добавлен в `.gitignore`, поэтому пароли не попадут в репозиторий):
```properties
GRID_USER=admin
GRID_PASS=ваш_супер_пароль
GRID_DOMAIN=selenium.ваш-домен.com
```
Запуск:
```bash
mvn test -Dtest="ui.RemoteGridTest"
```

**Запуск через GitHub Actions:**
В репозитории настроен ручной запуск тестов (`Run Remote UI Tests`). 
Для его работы добавьте следующие секреты в настройках репозитория GitHub (`Settings -> Secrets and variables -> Actions -> New repository secret`):
* `GRID_USER`
* `GRID_PASS`
* `GRID_DOMAIN`

После этого вы сможете запускать тесты на вашем сервере нажатием кнопки во вкладке `Actions`.

> [!WARNING]
> Никогда не коммитьте логины и пароли в исходный код (даже для тестовых контуров). Секретные данные должны передаваться исключительно через переменные окружения ОС на агенте сборки или через локальный `.env.properties`.