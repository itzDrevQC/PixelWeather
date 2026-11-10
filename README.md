# Pixel Weather

Material You weather app for Android. The interface is a single `weather.html` file rendered in a full-screen WebView. Fonts and weather icons are embedded, so the UI works offline; weather data is loaded from Open-Meteo.

## Features

- Current weather, hourly forecast, and multi-day forecast.
- City search and a list of popular cities.
- Light and dark themes, multiple interface languages.
- Unified customization: theme, Monet palette, icon ornament, custom fonts, and three weather icon sets.
- Animated wavy sun track in the Sun card.
- No ads and no sign-up.
- Weather data: [Open-Meteo](https://open-meteo.com/).

## Versions

Older versions are never deleted: every release stays available on GitHub.

| Version | Date | Release |
| --- | --- | --- |
| 1.2 | November 10, 2026 | [v1.2](https://github.com/itzDrevQC/PixelWeather/releases/tag/v1.2) |
| 1.1 | November 7, 2026 | [v1.1](https://github.com/itzDrevQC/PixelWeather/releases/tag/v1.1) |
| 1.0 | November 6, 2026 | [v1.0](https://github.com/itzDrevQC/PixelWeather/releases/tag/v1.0) |

Current version: [latest release](https://github.com/itzDrevQC/PixelWeather/releases/latest).

## Structure

```text
PixelWeather/
├── apk-build/
│   ├── AndroidManifest.xml
│   ├── build.ps1             # Builds the APK
│   ├── make-icons.ps1        # Generates PNG icons
│   ├── java/                 # Android wrapper sources
│   └── res/                  # App resources
├── .gitignore
└── README.md
```

The main app interface lives in `weather.html` in the project root.

## Building the APK

You need Windows with PowerShell 5.1+ and the following local components:

- JDK 17 — in `apk-build/jdk-17.0.21+9`;
- Android SDK Build-Tools (`aapt2`, `d8`, `zipalign`, `apksigner`) — in `apk-build/sdk/android-14`;
- Android 15 Platform (`android.jar`) — in `apk-build/sdk/android-35`.

These directories are not stored in the repository. Once they are in place, run the build from the project root:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\apk-build\build.ps1
```

The finished file appears in the root as `PixelWeather.apk`. The script creates a local `debug.keystore` if it does not exist yet; the key and APK are excluded from Git. To publish the file, use GitHub Releases instead of committing it to the repository.

## Credits

- **Comfortaa** — Johan Aakerlund, SIL Open Font License 1.1.
- **Noto Color Emoji** (2D PNG) — Google, Apache 2.0.
- **Open-Meteo** — open weather data API.
