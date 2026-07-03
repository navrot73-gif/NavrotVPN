#!/usr/bin/env bash
#
# build-libv2ray.sh
# Собирает libv2ray.aar из AndroidLibXrayLite (обёртка над xray-core,
# закрывает V2Ray/VMess/VLESS и Trojan — всё через один .aar, т.к. это
# один и тот же движок, просто разные outbound-протоколы).
#
# Требования на машине, где запускаешь (НЕ на телефоне и не здесь, в чате):
#   - Go 1.21+           (https://go.dev/dl/)
#   - Android SDK        (ANDROID_HOME указывает на него)
#   - Android NDK 25+     (обычно ставится через Android Studio SDK Manager)
#   - git
#
# Результат: libv2ray.aar в текущей директории — скопируй его в
# app/libs/libv2ray.aar проекта NavrotVPN.
#
set -euo pipefail

: "${ANDROID_HOME:?Установи переменную ANDROID_HOME (путь к Android SDK)}"

echo "==> Проверяю Go"
command -v go >/dev/null || { echo "Нужен Go: https://go.dev/dl/"; exit 1; }

echo "==> Ставлю gomobile"
go install golang.org/x/mobile/cmd/gomobile@latest
go install golang.org/x/mobile/cmd/gobind@latest
export PATH="$PATH:$(go env GOPATH)/bin"

echo "==> gomobile init"
gomobile init

WORKDIR="$(mktemp -d)"
echo "==> Клонирую AndroidLibXrayLite в ${WORKDIR}"
git clone --depth 1 https://github.com/2dust/AndroidLibXrayLite.git "${WORKDIR}/AndroidLibXrayLite"
cd "${WORKDIR}/AndroidLibXrayLite"

echo "==> go mod tidy"
go mod tidy

echo "==> Собираю libv2ray.aar (это может занять 10-20 минут на первый раз)"
gomobile bind -target=android -androidapi 24 -o libv2ray.aar .

cp libv2ray.aar "${OLDPWD}/libv2ray.aar"
echo ""
echo "==> Готово: $(pwd)/libv2ray.aar"
echo "    Скопируй его в app/libs/libv2ray.aar в проекте NavrotVPN."
