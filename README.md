# Pixel Weather

Material You weather app for Android. The interface is a single `weather.html` file rendered in a full-screen WebView. Fonts and weather icons are embedded, so the UI works offline; weather data is loaded from Open-Meteo.

## Возможности

- Текущая погода, почасовой прогноз и прогноз на несколько дней.
- Поиск города и список популярных городов.
- Светлая и тёмная темы, несколько языков интерфейса.
- Свои шрифты TTF/OTF и три набора иконок погоды: Noto, Material, Pixel.
- Без рекламы и регистрации.
- Погодные данные: [Open-Meteo](https://open-meteo.com/).

## Структура

```text
PixelWeather/
├── apk-build/
│   ├── AndroidManifest.xml
│   ├── build.ps1             # Сборка APK
│   ├── make-icons.ps1        # Генерация PNG-иконок
│   ├── java/                 # Исходники Android-обёртки
│   └── res/                  # Ресурсы приложения
├── .gitignore
└── README.md
```

Основной интерфейс приложения находится в `weather.html` в корне проекта.

## Сборка APK

Нужен Windows с PowerShell 5.1+ и следующие локальные компоненты:

- JDK 17 — в `apk-build/jdk-17.0.21+9`;
- Android SDK Build-Tools (`aapt2`, `d8`, `zipalign`, `apksigner`) — в `apk-build/sdk/android-14`;
- Android 15 Platform (`android.jar`) — в `apk-build/sdk/android-35`.

Эти каталоги не хранятся в репозитории. После их размещения сборка запускается из корня проекта:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\apk-build\build.ps1
```

Готовый файл появится в корне как `PixelWeather.apk`. Скрипт создаёт локальный `debug.keystore`, если его ещё нет; ключ и APK исключены из Git. Для публикации файла используйте GitHub Releases, а не коммит в репозиторий.

## Благодарности

- **Comfortaa** — Johan Aakerlund, лицензия SIL Open Font License 1.1.
- **Noto Color Emoji** (2D PNG) — Google, лицензия Apache 2.0.
- **Open-Meteo** — открытый API погодных данных.
