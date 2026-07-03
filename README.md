# NavrotVPN

Android-клиент VPN на Kotlin с рабочими протоколами WireGuard и
AmneziaWG (обфускация против DPI), заготовками под ещё 6 протоколов,
kill-switch через системный Always-on VPN, авто-переподключением при
смене сети, генерацией ключей на устройстве и скриптом развёртывания
собственного бесплатного сервера.

## Протоколы

| Протокол | Статус | Детали |
|---|---|---|
| **WireGuard** | ✅ Работает | `com.wireguard.android:tunnel` — официальная библиотека |
| **AmneziaWG** | ✅ Работает | `com.zaneschepke:amneziawg-android` — форк WireGuard с обфускацией трафика против DPI (мусорные пакеты + случайные заголовки). Реальная антиблокировка |
| **V2Ray / VMess / VLESS** | ✅ Работает* | xray-core через `libv2ray.aar` (см. `xray-build/`). Ты собираешь `.aar` сам — в репозитории бинарник не хранится |
| **Trojan** | ✅ Работает* | тот же xray-core/`libv2ray.aar`, просто другой outbound в конфиге |
| Shadowsocks | ⚠ Заглушка | нужен AIDL-мост к shadowsocks-android либо форк его core |
| Hysteria2 | ⚠ Заглушка | нужна сборка hysteria-core под Android через gomobile |
| OpenVPN | ⚠ Заглушка | нужен NDK-модуль из ics-openvpn |
| IKEv2/IPsec | ⚠ Заглушка | strongSwan-модуль (NDK) либо системный `Ikev2VpnProfile` на Android 11+ |

\* V2Ray и Trojan помечены рабочими в коде (реальная интеграция с
`CoreController.startLoop()`, TUN отдаётся напрямую в xray-core), но
физически заработают только после того, как ты соберёшь `libv2ray.aar` —
см. раздел "V2Ray/Trojan: сборка .aar" ниже.

**Важно:** если `libv2ray.aar` отсутствует в `app/libs/`, он автоматически
исключается из зависимостей — WireGuard и AmneziaWG соберутся без ошибок.

Выбор протокола — часть модели сервера (`ServerModel.protocol`), UI сам
подписывается на состояние нужного обработчика через `ProtocolRouter`.
У каждой заглушки в комментариях — точный путь к реальной интеграции.

## Архитектура (после доработки)

```
┌──────────────┐
│  MainActivity  │ ← только UI, подписка на ViewModel
└──────┬───────┘
       │
┌──────▼───────┐
│  VpnViewModel  │ ← бизнес-логика, переживает поворот экрана
└──────┬───────┘
       │
┌──────▼───────────┐
│  ProtocolRouter    │ ← маршрутизация по протоколу
└──────┬───────────┘
       │
  ┌────┼────────────┬────────────┬──────────────┐
  ▼    ▼            ▼            ▼              ▼
WG  AmneziaWG   XrayManager   Stub         Stub
Mgr   Mgr      (V2Ray/Trojan)  (SS/Hy2/OV/IKE)
```

## Безопасность

- **allowBackup=false** — приватные ключи не попадут в ADB backup
- **networkSecurityConfig** — запрещён HTTP (кроме localhost для отладки)
- **EncryptedSharedPreferences** — AES-256 для приватного ключа, ключ в Android Keystore
- **BootReceiver отключён по умолчанию** — включается только с согласия пользователя
- **ProGuard** включён для release-сборки (obfuscation + shrinkResources)
- **OkHttp таймауты** — 10с connect/read/write (было: бесконечный hang)
- **START_NOT_STICKY** для V2RayVpnService — не перезапускается при системном kill

## Антиблокировка

- **AmneziaWG** — основной реальный механизм. Параметры Jc/Jmin/Jmax/
  S1/S2/H1-H4 задаются на сервер и клиент и превращают WireGuard-трафик
  в трафик без узнаваемой DPI-сигнатуры.
- **Kill switch** — `VpnServiceImpl` и `AmneziaVpnServiceImpl` объявлены
  с `android:supportsAlwaysOn="true"`.
- **Авто-переподключение** — `NetworkMonitor` следит за
  `ConnectivityManager` и переподключает при смене сети с валидацией
  интернет-соединения.
- **Свой сервер** — см. `server-deploy/`.

## Ключи

`KeyManager` генерирует пару Curve25519 прямо на устройстве и хранит
приватный ключ в `EncryptedSharedPreferences` (AES-256, ключ шифрования —
в Android Keystore). Приватный ключ никогда не покидает устройство.

## UI

Главный экран переработан в карточки: статус подключения, выбор сервера
(с пометкой ⚠ у ещё не реализованных протоколов), параметры обфускации
для AmneziaWG, управление ключами устройства, kill switch.

ViewModel (`VpnViewModel`) вынесен из Activity — состояние подключения
сохраняется при повороте экрана. Цвета статусов дифференцированы.
Регенерация ключей требует подтверждения через диалог.

## Свой бесплатный сервер (`server-deploy/`)

- `deploy-wireguard-awg.sh` — скрипт, разворачивающий WireGuard и/или
  AmneziaWG одной командой.
- Исправлена генерация магических заголовков H1-H4 (ранее `RANDOM * RANDOM`
  давала отрицательные числа и переполнение).
- Добавлен IPv6 forwarding.
- Добавлена рандомизация Jmin/Jmax.
- `add-wg`/`add-awg` обрабатываются до установки пакетов.

## V2Ray/Trojan: сборка .aar

```bash
cd xray-build
./build-libv2ray.sh
cp libv2ray.aar ../app/libs/libv2ray.aar
```

## Перед первой сборкой — обязательно

1. Разверни свой сервер (`server-deploy/`) и замени плейсхолдеры в
   `app/src/main/assets/servers.json` на реальные данные.
2. `minSdk 24` — требование библиотек WireGuard/AmneziaWG.
3. Если V2Ray/Trojan пока не нужны — просто не клади `libv2ray.aar`
   в `app/libs/`, сборка пройдёт корректно.
4. Установи Gradle Wrapper: `gradle wrapper --gradle-version 8.5` (или
   используй файлы из `gradle/wrapper/`).

## Чеклист доработок

- [x] Условная зависимость libv2ray.aar (сборка без него)
- [x] ProGuard для release (minify + shrinkResources)
- [x] gradle.properties с рекомендуемыми настройками
- [x] .gitignore (ключи, build, IDE, AAR)
- [x] allowBackup=false + fullBackupContent=false
- [x] networkSecurityConfig (запрет HTTP)
- [x] BootReceiver: enabled=false по умолчанию
- [x] OkHttpClient таймауты (10с)
- [x] ApiClient: логирование + about:blank вместо example.com
- [x] VpnViewModel: сохранение состояния при повороте
- [x] MainActivity: диалог подтверждения регенерации ключей
- [x] WireGuardManager: убрано дублирование CONNECTED state
- [x] AmneziaWgManager: безопасная init (synchronized + volatile)
- [x] XrayManager: устранён memory leak через статический callback
- [x] V2RayVpnService: START_NOT_STICKY, IPv6, named thread, UTLS fingerprint
- [x] ProtocolRouter: ленивая инициализация, runtime-проверка libv2ray
- [x] NetworkMonitor: валидация capabilities, безопасная отписка
- [x] Deploy-скрипт: исправлена генерация H1-H4, добавлен IPv6
- [x] XrayConfigBuilder: IPv6 DNS, валидация, UTLS fingerprint
- [x] Цвет status_connecting
- [x] proguard-rules.pro с keep-правилами
- [x] gradle-wrapper.properties
- [x] Логирование по всему проекту