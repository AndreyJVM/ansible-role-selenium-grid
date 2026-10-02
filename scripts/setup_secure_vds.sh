#!/bin/bash
# Скрипт инициализации и защиты VDS (Ubuntu 22.04/24.04, Debian 12).
# Выполнение разрешено только от пользователя root.

set -e

# ==========================================
# Инициализация переменных
# ==========================================
DEPLOY_USER=${DEPLOY_USER:-"deployer"}
TIMEZONE=${TIMEZONE:-"UTC"}

echo "[INFO] Инициализация настройки сервера..."

echo "[INFO] Обновление системы и установка утилит..."
export DEBIAN_FRONTEND=noninteractive
apt-get update -y
apt-get upgrade -y
apt-get install -y sudo curl wget git ufw fail2ban apache2-utils apt-transport-https ca-certificates software-properties-common lsb-release openssh-server chrony tzdata

echo "[INFO] Настройка часового пояса ($TIMEZONE) и синхронизации времени (Chrony)..."
ln -fs "/usr/share/zoneinfo/$TIMEZONE" /etc/localtime
echo "$TIMEZONE" > /etc/timezone
if command -v timedatectl &>/dev/null; then
    timedatectl set-timezone "$TIMEZONE" 2>/dev/null || true
fi
systemctl enable chrony 2>/dev/null || true
systemctl start chrony 2>/dev/null || true
echo "[INFO] Текущее время сервера: $(date)"

echo "[INFO] Проверка и создание служебного пользователя ($DEPLOY_USER)..."
if id "$DEPLOY_USER" &>/dev/null; then
    echo "[INFO] Пользователь $DEPLOY_USER уже существует. Шаг пропущен."
else
    useradd -m -s /bin/bash "$DEPLOY_USER"
    usermod -aG sudo "$DEPLOY_USER"
    
    mkdir -p /etc/sudoers.d
    echo "$DEPLOY_USER ALL=(ALL) NOPASSWD:ALL" > /etc/sudoers.d/$DEPLOY_USER
    chmod 440 /etc/sudoers.d/$DEPLOY_USER
    
    mkdir -p /home/$DEPLOY_USER/.ssh
    if [ -f /root/.ssh/authorized_keys ]; then
        cp /root/.ssh/authorized_keys /home/$DEPLOY_USER/.ssh/
        chown -R $DEPLOY_USER:$DEPLOY_USER /home/$DEPLOY_USER/.ssh
        chmod 700 /home/$DEPLOY_USER/.ssh
        chmod 600 /home/$DEPLOY_USER/.ssh/authorized_keys
    fi
    echo "[SUCCESS] Пользователь $DEPLOY_USER изолирован и настроен."
fi

echo "[INFO] Конфигурация среды Docker..."
if ! command -v docker &> /dev/null; then
    curl -fsSL https://download.docker.com/linux/ubuntu/gpg | apt-key add -
    add-apt-repository -y "deb [arch=amd64] https://download.docker.com/linux/ubuntu $(lsb_release -cs) stable"
    apt-get update -y
    apt-get install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin
    
    usermod -aG docker "$DEPLOY_USER"
else
    echo "[INFO] Docker уже установлен."
fi

echo "[INFO] Конфигурация Firewall (UFW)..."
ufw --force reset
ufw default deny incoming
ufw default allow outgoing
ufw allow ssh
ufw allow http
ufw allow https
ufw --force enable

echo "[INFO] Харденинг аутентификации (Fail2Ban & SSH)..."
systemctl enable fail2ban
systemctl start fail2ban

if [ -f /etc/ssh/sshd_config ]; then
    sed -i 's/^#*PermitRootLogin.*/PermitRootLogin no/' /etc/ssh/sshd_config
    sed -i 's/^#*PasswordAuthentication.*/PasswordAuthentication no/' /etc/ssh/sshd_config
    systemctl reload sshd || systemctl restart sshd || true
fi

echo "[INFO] Подготовка директорий проекта..."
mkdir -p /opt/ui-tests-infra
chown -R $DEPLOY_USER:$DEPLOY_USER /opt/ui-tests-infra

echo "[SUCCESS] Базовая настройка сервера завершена успешно."
echo "[IMPORTANT] Прямой доступ для пользователя root заблокирован. Используйте логин: $DEPLOY_USER"
