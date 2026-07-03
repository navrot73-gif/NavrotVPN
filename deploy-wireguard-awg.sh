#!/usr/bin/env bash
#
# deploy-wireguard-awg.sh
# Разворачивает на чистом Ubuntu 22.04/24.04 сервере WireGuard и/или AmneziaWG
# и печатает всё для вставки в servers.json NavrotVPN.
#
# Запуск (на сервере, от root):
#   sudo ./deploy-wireguard-awg.sh wg      # только WireGuard
#   sudo ./deploy-wireguard-awg.sh awg     # только AmneziaWG
#   sudo ./deploy-wireguard-awg.sh both    # оба сразу
#   sudo ./deploy-wireguard-awg.sh add-wg  <PUBLIC_KEY> <IP/32>
#   sudo ./deploy-wireguard-awg.sh add-awg <PUBLIC_KEY> <IP/32>
#
set -euo pipefail

MODE="${1:-both}"
WG_PORT="${WG_PORT:-51820}"
AWG_PORT="${AWG_PORT:-51821}"
SUBNET_WG="10.66.66.1/24"
SUBNET_AWG="10.77.77.1/24"
SERVER_IP="$(curl -s -4 --connect-timeout 5 ifconfig.me || curl -s -4 --connect-timeout 5 icanhazip.com || echo 'UNKNOWN')"
SERVER_IPV6="$(curl -s -6 --connect-timeout 5 ifconfig.co 2>/dev/null || echo '')"
IFACE="$(ip -o -4 route show to default | awk '{print $5}' | head -n1)"

echo "==> Внешний IPv4: ${SERVER_IP}"
[ -n "$SERVER_IPV6" ] && echo "==> Внешний IPv6: ${SERVER_IPV6}"
echo "==> Сетевой интерфейс: ${IFACE}"

apt update -y
apt install -y curl gnupg2 iptables

# ── Вспомогательные функции ────────────────────────────────────────────

enable_forwarding() {
  sysctl -w net.ipv4.ip_forward=1 >/dev/null
  sysctl -w net.ipv6.conf.all.forwarding=1 >/dev/null 2>&1 || true
  grep -q '^net.ipv4.ip_forward' /etc/sysctl.conf || echo 'net.ipv4.ip_forward=1' >> /etc/sysctl.conf
  grep -q '^net.ipv6.conf.all.forwarding' /etc/sysctl.conf || echo 'net.ipv6.conf.all.forwarding=1' >> /etc/sysctl.conf 2>/dev/null || true
}

# Генерирует случайное 32-битное беззнаковое число (ИСПРАВЛЕНО: было RANDOM * RANDOM)
random_uint32() {
  echo $(( (RANDOM << 16) | RANDOM ))
}

# ── WireGuard ──────────────────────────────────────────────────────────

setup_wireguard() {
  echo "==> Устанавливаю WireGuard"
  apt install -y wireguard wireguard-tools

  umask 077
  mkdir -p /etc/wireguard
  wg genkey | tee /etc/wireguard/server_private.key | wg pubkey > /etc/wireguard/server_public.key
  PRIV=$(cat /etc/wireguard/server_private.key)
  PUB=$(cat /etc/wireguard/server_public.key)

  cat > /etc/wireguard/wg0.conf <<EOF
[Interface]
Address = ${SUBNET_WG}
ListenPort = ${WG_PORT}
PrivateKey = ${PRIV}
PostUp = iptables -A FORWARD -i wg0 -j ACCEPT; iptables -t nat -A POSTROUTING -o ${IFACE} -j MASQUERADE
PostDown = iptables -D FORWARD -i wg0 -j ACCEPT; iptables -t nat -D POSTROUTING -o ${IFACE} -j MASQUERADE
EOF

  systemctl enable --now wg-quick@wg0

  echo ""
  echo "===== WireGuard готов ====="
  echo "protocol:        WIREGUARD"
  echo "endpoint:        ${SERVER_IP}:${WG_PORT}"
  echo "serverPublicKey: ${PUB}"
  echo "clientAddress:   10.66.66.2/32"
  echo "allowedIps:      0.0.0.0/0, ::/0"
  echo "dns:             1.1.1.1, 1.0.0.1"
  echo "============================"
}

# ── AmneziaWG ──────────────────────────────────────────────────────────

setup_amneziawg() {
  echo "==> Подключаю PPA Amnezia и ставлю AmneziaWG"
  apt install -y software-properties-common
  add-apt-repository -y ppa:amnezia/ppa
  apt update -y
  apt install -y amneziawg

  umask 077
  mkdir -p /etc/amnezia/amneziawg
  awg genkey | tee /etc/amnezia/amneziawg/server_private.key | awg pubkey > /etc/amnezia/amneziawg/server_public.key
  PRIV=$(cat /etc/amnezia/amneziawg/server_private.key)
  PUB=$(cat /etc/amnezia/amneziawg/server_public.key)

  # Рекомендованные диапазоны AmneziaWG: Jc 4-12
  JC=$(( (RANDOM % 9) + 4 ))
  JMIN=$(( (RANDOM % 20) + 30 ))   # 30-50
  JMAX=$(( JMIN + (RANDOM % 40) + 20 ))  # JMIN+20..JMIN+60

  # Уникальные магические заголовки — ИСПРАВЛЕНО: корректная генерация uint32
  H1=$(random_uint32)
  H2=$(random_uint32)
  H3=$(random_uint32)
  H4=$(random_uint32)
  # Гарантируем что все H уникальны
  while [ "$H2" = "$H1" ]; do H2=$(random_uint32); done
  while [ "$H3" = "$H1" ] || [ "$H3" = "$H2" ]; do H3=$(random_uint32); done
  while [ "$H4" = "$H1" ] || [ "$H4" = "$H2" ] || [ "$H4" = "$H3" ]; do H4=$(random_uint32); done

  cat > /etc/amnezia/amneziawg/awg0.conf <<EOF
[Interface]
Address = ${SUBNET_AWG}
ListenPort = ${AWG_PORT}
PrivateKey = ${PRIV}
Jc = ${JC}
Jmin = ${JMIN}
Jmax = ${JMAX}
S1 = 0
S2 = 0
H1 = ${H1}
H2 = ${H2}
H3 = ${H3}
H4 = ${H4}
PostUp = iptables -A FORWARD -i awg0 -j ACCEPT; iptables -t nat -A POSTROUTING -o ${IFACE} -j MASQUERADE
PostDown = iptables -D FORWARD -i awg0 -j ACCEPT; iptables -t nat -D POSTROUTING -o ${IFACE} -j MASQUERADE
EOF

  systemctl enable --now awg-quick@awg0

  echo ""
  echo "===== AmneziaWG готов ====="
  echo "protocol:        AMNEZIAWG"
  echo "endpoint:        ${SERVER_IP}:${AWG_PORT}"
  echo "serverPublicKey: ${PUB}"
  echo "clientAddress:   10.77.77.2/32"
  echo "allowedIps:      0.0.0.0/0, ::/0"
  echo "dns:             1.1.1.1, 1.0.0.1"
  echo "jc: ${JC}, jMin: ${JMIN}, jMax: ${JMAX}, s1: 0, s2: 0"
  echo "h1: ${H1}, h2: ${H2}, h3: ${H3}, h4: ${H4}"
  echo "============================"
}

# ── Добавление клиентов ────────────────────────────────────────────────

add_wg_client() {
  local pubkey="$1" address="$2"
  wg set wg0 peer "$pubkey" allowed-ips "$address"
  wg-quick save wg0 2>/dev/null || true
  echo "Клиент WireGuard добавлен: ${pubkey:0:16}... -> ${address}"
}

add_awg_client() {
  local pubkey="$1" address="$2"
  awg set awg0 peer "$pubkey" allowed-ips "$address"
  awg-quick save awg0 2>/dev/null || true
  echo "Клиент AmneziaWG добавлен: ${pubkey:0:16}... -> ${address}"
}

# ── Парсинг и запуск ───────────────────────────────────────────────────

enable_forwarding

# Сначала обрабатываем команды add-* (до case, чтобы не пытаться ставить пакеты)
if [ "${MODE}" = "add-wg" ]; then
  add_wg_client "${2:?Укажи PUBLIC_KEY}" "${3:?Укажи IP/32}"
  exit 0
fi
if [ "${MODE}" = "add-awg" ]; then
  add_awg_client "${2:?Укажи PUBLIC_KEY}" "${3:?Укажи IP/32}"
  exit 0
fi

case "$MODE" in
  wg)   setup_wireguard ;;
  awg)  setup_amneziawg ;;
  both) setup_wireguard; setup_amneziawg ;;
  *)    echo "Использование: $0 [wg|awg|both|add-wg|add-awg]"; exit 1 ;;
esac

echo ""
echo "Готово. Не забудь:"
echo "  1. Открыть порт(ы) ${WG_PORT}/udp и/или ${AWG_PORT}/udp в firewall облачного провайдера"
echo "     (Security Group / Firewall Rules) — это ОТДЕЛЬНО от ufw/iptables на сервере."
echo ""
echo "  2. Добавить публичный ключ устройства с NavrotVPN:"
echo "     sudo bash $0 add-wg  <PUBLIC_KEY> 10.66.66.2/32"
echo "     sudo bash $0 add-awg <PUBLIC_KEY> 10.77.77.2/32"