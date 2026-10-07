# File Browser Mobile

Android-клиент (WebView-обёртка) для self-hosted сервиса
[FileBrowser Quantum](https://github.com/gtsteffaniak/filebrowser).

## Возможности

- 🔐 Сохранение сессии (cookies + localStorage) между запусками
- 📤 Загрузка файлов через системный Android File Picker
- 📥 Скачивание через Android DownloadManager (файлы в `/Downloads`)
- 🔄 Pull-to-refresh
- 🎨 Поддержка светлой/тёмной темы
- 🌐 Работа с любым self-hosted FileBrowser (HTTP/HTTPS)
- ⚠️ Понятные экраны ошибок вместо системных страниц WebView

## Требования

- Android 8.0+ (API 26)
- [FileBrowser Quantum](https://github.com/gtsteffaniak/filebrowser) на вашем сервере

## Скачать

Скачайте APK из [Releases](../../releases/latest).

## Сборка из исходников

### Debug

```bash
./gradlew assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

### Release

Создайте `keystore.properties` в корне проекта:

```properties
storeFile=filebrowser-mobile-release.jks
storePassword=ВАШ_ПАРОЛЬ
keyAlias=filebrowser-mobile
keyPassword=ВАШ_ПАРОЛЬ
```

Положите рядом `filebrowser-mobile-release.jks`. Затем:

```bash
./gradlew assembleRelease
```

APK: `app/build/outputs/apk/release/app-release.apk`

## Настройка

1. Запустите приложение.
2. Введите адрес FileBrowser: `https://files.example.com` или `http://192.168.1.100:8080`.
3. Авторизуйтесь (поддерживается 2FA).
4. Пользуйтесь.

## Безопасность

- SSL-сертификаты **не отключаются**.
- Cleartext (HTTP) разрешён осознанно для self-hosted инсталляций.
- Для внешнего доступа — HTTPS + VPN.

## Лицензия

MIT — см. [LICENSE](LICENSE).