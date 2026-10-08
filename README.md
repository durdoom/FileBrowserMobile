# File Browser Mobile

Android WebView client for self-hosted [FileBrowser Quantum](https://github.com/gtsteffaniak/filebrowser).

## Features

- 🔐 Session persistence (cookies + localStorage) between launches
- 📤 Upload files via system Android File Picker
- 📥 Download via Android DownloadManager (saved to `/Downloads`)
- 🔄 Pull-to-refresh
- 🎨 Light/dark theme support
- 🌐 Works with any self-hosted FileBrowser (HTTP/HTTPS)
- ⚠️ Friendly error screens instead of WebView's built-in error pages
- 🌍 Localized UI (English, Russian)

## Requirements

- Android 8.0+ (API 26)
- A [FileBrowser Quantum](https://github.com/gtsteffaniak/filebrowser) instance running on your server

## Download

Grab the APK from [Releases](../../releases/latest).

## Building from source

### Requirements

- Android Studio Ladybug (2024.2) or newer
- JDK 17
- Android SDK 35
- Gradle 8.7+

### Debug build

```bash
./gradlew assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

### Release build

Create `keystore.properties` in the project root:

```properties
storeFile=filebrowser-mobile-release.jks
storePassword=YOUR_PASSWORD
keyAlias=filebrowser-mobile
keyPassword=YOUR_PASSWORD
```

Place `filebrowser-mobile-release.jks` next to it. Then:

```bash
./gradlew assembleRelease
```

APK: `app/build/outputs/apk/release/app-release.apk`

## Setup

1. Launch the app.
2. Enter your FileBrowser URL:
   - `https://files.example.com`
   - `http://192.168.1.100:8080`
3. Sign in (2FA is supported).
4. Use the app.

## Security

- SSL certificates are **never bypassed**.
- Cleartext (HTTP) is allowed **intentionally** for self-hosted setups.
- For external access, use HTTPS + VPN + reverse proxy.

## Tech stack

- Kotlin, AndroidX
- WebView (Android System WebView)
- Material Components
- No Jetpack Compose, no Room, no Firebase

## License

MIT — see [LICENSE](LICENSE).

## Credits

- [FileBrowser Quantum](https://github.com/gtsteffaniak/filebrowser) — server-side
- Icon — Material Symbols (folder)