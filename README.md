# GrokPlayer TV

Android TV / Google TV client for the GrokPlayer ecosystem. First stage is the 10-foot home screen; playback, library, streams, downloads, and settings will be wired later.

Later targets (not in this tree yet): Samsung Tizen and LG webOS.

## What this build does

- Kotlin + Jetpack Compose for TV
- Home screen matches `01-ana-ekran.png` (Ana sayfa, continue-watching hero, Son açılanlar)
- Videolar, Akışlar, İndirilenler, Ayarlar open a **Yakında** placeholder
- Video actions are placeholders (`Devam et`, `Baştan oynat`, cards, search)

## Requirements

- Android Studio Ladybug+ or JDK 17/21 (Android Studio JBR works)
- Android SDK 35, minSdk 24, an Android TV / Google TV device or emulator (1080p)

## Build and install

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat installDebug
```

The Television_1080p AVD on `emulator-5554` is the first-stage target.

Application id: `com.grokplayer.tv`
