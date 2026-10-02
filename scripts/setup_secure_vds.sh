#!/bin/bash
# ==============================================================================
# VDS Initialization and Hardening Script
# Supported OS: Ubuntu 22.04 / 24.04, Debian 12
# ==============================================================================
set -eo pipefail

# ==========================================
# Конфигурация (Configuration)
# ==========================================
DEPLOY_USER=${DEPLOY_USER:-"deployer"}
TIMEZONE=${TIMEZONE:-"UTC"}
SSH_PORT=${SSH_PORT:-"22"}

# ==========================================
# Логирование (Logging)
# ==========================================
COLOR_INFO='\033[0;36m'
COLOR_SUCCESS='\033[0;32m'
COLOR_ERROR='\033[0;31m'
COLOR_WARNING='\033[0;33m'
NC='\033[0m' # No Color

log_info()    { echo -e "${COLOR_INFO}[INFO] $1${NC}"; }
log_success() { echo -e "${COLOR_SUCCESS}[SUCCESS] $1${NC}"; }
log_error()   { echo -e "${COLOR_ERROR}[ERROR] $1${NC}" >&2; }
log_warn()    { echo -e "${COLOR_WARNING}[WARNING] $1${NC}"; }

# ==========================================
# Проверка прав (Root Check)
# ==========================================
if [[ "${EUID}" -ne 0 ]]; then
    log_error "Скрипт должен быть запущен с правами root (например: sudo ./setup_secure_vds.sh)"
    exit 1
fi

# ==========================================
# 1. Настройка времени (До установки пакетов)
# ==========================================
# В Ubuntu 22.04 установка tzdata может зависнуть на интерактивном диалоге,
# поэтому мы преднастраиваем таймзону ДО вызова apt-get install
log_info "Шаг 1: Предварительная настройка часового пояса ($TIMEZONE)..."
export DEBIAN_FRONTEND=noninteractive
export TZ="$TIMEZONE"
ln -fs "/usr/share/zoneinfo/$TIMEZONE" /etc/localtime
echo "$TIMEZONE" > /etc/timezone

# ==========================================
# 2. Базовые обновления и пакеты
# ==========================================
log_info "Шаг 2: Обновление системы и установка базовых утилит..."
apt-get update -qq
apt-get upgrade -qq -y
apt-get install -qq -y sudo curl wget git ufw fail2ban apache2-utils \
    apt-transport-https ca-certificates software-properties-common lsb-release openssh-server chrony tzdata

# Применяем таймзону и стартуем chrony
dpkg-reconfigure -f noninteractive tzdata 2>/dev/null || true
if command -v timedatectl &>/dev/null; then
    timedatectl set-timezone "$TIMEZONE" 2>/dev/null || true
fi
systemctl enable chrony 2>/dev/null || true
systemctl start chrony 2>/dev/null || true

# ==========================================
# 3. Служебный пользователь
# ==========================================
log_info "Шаг 3: Настройка пользователя '$DEPLOY_USER'..."
if id "$DEPLOY_USER" &>/dev/null; then
    log_warn "Пользователь $DEPLOY_USER уже существует."
else
    useradd -m -s /bin/bash "$DEPLOY_USER"
    usermod -aG sudo "$DEPLOY_USER"
    
    # Беспарольный sudo
    mkdir -p /etc/sudoers.d
    echo "$DEPLOY_USER ALL=(ALL) NOPASSWD:ALL" > "/etc/sudoers.d/$DEPLOY_USER"
    chmod 440 "/etc/sudoers.d/$DEPLOY_USER"
    
    # Перенос SSH-ключей от root
    mkdir -p "/home/$DEPLOY_USER/.ssh"
    if [[ -f /root/.ssh/authorized_keys ]]; then
        cp /root/.ssh/authorized_keys "/home/$DEPLOY_USER/.ssh/"
        chown -R "$DEPLOY_USER:$DEPLOY_USER" "/home/$DEPLOY_USER/.ssh"
        chmod 700 "/home/$DEPLOY_USER/.ssh"
        chmod 600 "/home/$DEPLOY_USER/.ssh/authorized_keys"
    fi
    log_success "Пользователь $DEPLOY_USER успешно изолирован."
fi

# ==========================================
# 4. Установка Docker
# ==========================================
log_info "Шаг 4: Установка Docker Engine..."
if ! command -v docker &> /dev/null; then
    install -m 0755 -d /etc/apt/keyrings
    curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
    chmod a+r /etc/apt/keyrings/docker.asc

    # Определяем ID дистрибутива (ubuntu или debian)
    OS_ID=$(lsb_release -is | tr '[:upper:]' '[:lower:]')
    OS_CODENAME=$(lsb_release -cs)

    echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/$OS_ID $OS_CODENAME stable" | \
        tee /etc/apt/sources.list.d/docker.list > /dev/null

    apt-get update -qq
    apt-get install -qq -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
    
    usermod -aG docker "$DEPLOY_USER"
    log_success "Docker успешно установлен."
else
    log_warn "Docker уже установлен, пропускаем шаг."
fi

# ==========================================
# 5. Firewall (UFW)
# ==========================================
log_info "Шаг 5: Настройка Firewall (UFW)..."
ufw --force reset >/dev/null
ufw default deny incoming >/dev/null
ufw default allow outgoing >/dev/null
ufw allow "$SSH_PORT/tcp" >/dev/null
ufw allow 80/tcp >/dev/null
ufw allow 443/tcp >/dev/null
ufw --force enable >/dev/null

# ==========================================
# 6. Харденинг SSH и Fail2Ban
# ==========================================
log_info "Шаг 6: Харденинг SSH и защита Fail2Ban..."
systemctl enable fail2ban 2>/dev/null || true
systemctl start fail2ban 2>/dev/null || true

if [[ -f /etc/ssh/sshd_config ]]; then
    sed -i 's/^#*PermitRootLogin.*/PermitRootLogin no/' /etc/ssh/sshd_config
    sed -i 's/^#*PasswordAuthentication.*/PasswordAuthentication no/' /etc/ssh/sshd_config
    
    # Если нестандартный порт SSH - добавляем его в конфиг
    if ! grep -q "^Port $SSH_PORT" /etc/ssh/sshd_config; then
        echo "Port $SSH_PORT" >> /etc/ssh/sshd_config
    fi

    systemctl reload sshd 2>/dev/null || systemctl restart sshd 2>/dev/null || true
fi

# ==========================================
# 7. Подготовка директорий
# ==========================================
log_info "Шаг 7: Подготовка инфраструктуры проекта..."
mkdir -p /opt/ui-tests-infra
chown -R "$DEPLOY_USER:$DEPLOY_USER" /opt/ui-tests-infra

# ==========================================
# Завершение
# ==========================================
echo ""
log_success "==================================================================="
log_success "✅ VDS успешно инициализирован и защищен!"
log_info "Дальнейший доступ под 'root' заблокирован (или рекомендуется закрыть)."
log_info "Используйте логин: ssh -p $SSH_PORT $DEPLOY_USER@<server_ip>"
log_success "==================================================================="
