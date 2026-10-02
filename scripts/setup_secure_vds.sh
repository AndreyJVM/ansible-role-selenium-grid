#!/bin/bash
# Скрипт инициализации и защиты чистого VDS (Ubuntu 22.04/24.04).
# Требует запуска от пользователя root.

set -e # Остановка при любой ошибке

# --- НАСТРОЙКИ (передаются через переменные окружения из GitHub Actions) ---
DEPLOY_USER=${DEPLOY_USER:-"deployer"}

echo "=========================================="
echo "🚀 Начало настройки сервера..."
echo "=========================================="

echo "[1/6] Обновление системы и установка базовых утилит..."
export DEBIAN_FRONTEND=noninteractive
apt-get update -y
apt-get upgrade -y
apt-get install -y curl wget git ufw fail2ban apache2-utils apt-transport-https ca-certificates software-properties-common

echo "[2/6] Создание пользователя $DEPLOY_USER и копирование SSH-ключей..."
if id "$DEPLOY_USER" &>/dev/null; then
    echo "Пользователь $DEPLOY_USER уже существует. Пропускаем..."
else
    # Создаем пользователя без пароля (доступ только по ключу)
    useradd -m -s /bin/bash "$DEPLOY_USER"
    usermod -aG sudo "$DEPLOY_USER"
    # Разрешаем ему выполнять sudo без ввода пароля (удобно для автоматизации)
    echo "$DEPLOY_USER ALL=(ALL) NOPASSWD:ALL" > /etc/sudoers.d/$DEPLOY_USER
    
    # Копируем SSH ключ от root к новому пользователю
    mkdir -p /home/$DEPLOY_USER/.ssh
    if [ -f /root/.ssh/authorized_keys ]; then
        cp /root/.ssh/authorized_keys /home/$DEPLOY_USER/.ssh/
        chown -R $DEPLOY_USER:$DEPLOY_USER /home/$DEPLOY_USER/.ssh
        chmod 700 /home/$DEPLOY_USER/.ssh
        chmod 600 /home/$DEPLOY_USER/.ssh/authorized_keys
    fi
    echo "Пользователь $DEPLOY_USER успешно создан!"
fi

echo "[3/6] Установка Docker & Docker Compose v2..."
if ! command -v docker &> /dev/null; then
    curl -fsSL https://download.docker.com/linux/ubuntu/gpg | apt-key add -
    add-apt-repository -y "deb [arch=amd64] https://download.docker.com/linux/ubuntu $(lsb_release -cs) stable"
    apt-get update -y
    apt-get install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin
    
    # Добавляем нового пользователя в группу docker
    usermod -aG docker "$DEPLOY_USER"
else
    echo "Docker уже установлен."
fi

echo "[4/6] Настройка Firewall (UFW)..."
ufw --force reset
ufw default deny incoming
ufw default allow outgoing
ufw allow ssh     # Открываем порт 22
ufw allow http    # Открываем порт 80 (для Let's Encrypt и редиректов)
ufw allow https   # Открываем порт 443 (для Selenium)
# Включаем брандмауэр
ufw --force enable

echo "[5/6] Настройка защиты (Fail2Ban & SSH Hardening)..."
# Включаем fail2ban для защиты от подбора паролей к SSH
systemctl enable fail2ban
systemctl start fail2ban

# Отключаем вход по root (PermitRootLogin no)
sed -i 's/^#*PermitRootLogin.*/PermitRootLogin no/' /etc/ssh/sshd_config
# Отключаем вход по паролям (сохраняем только ключи)
sed -i 's/^#*PasswordAuthentication.*/PasswordAuthentication no/' /etc/ssh/sshd_config
# Применяем конфигурацию SSH (не перезапускает текущую сессию)
systemctl reload sshd || systemctl restart sshd

echo "[6/6] Подготовка файловой системы для проекта..."
mkdir -p /opt/ui-tests-infra
chown -R $DEPLOY_USER:$DEPLOY_USER /opt/ui-tests-infra

echo "=========================================="
echo "✅ Базовая настройка сервера успешно завершена!"
echo "❗️ ВАЖНО: Вход по SSH для root теперь запрещен. Используйте логин: $DEPLOY_USER"
echo "=========================================="
