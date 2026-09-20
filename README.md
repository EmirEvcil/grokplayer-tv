# GrokPlayer TV

Android TV / Google TV client for the GrokPlayer ecosystem. Leanback shell in Kotlin + Jetpack Compose. Overlay player (ExoPlayer + libVLC for AVI). LAN-only link to the Windows app.

Companion apps: [GrokPlayer (Windows)](https://github.com/EmirEvcil/grokplayer) · [Chrome extension](https://github.com/EmirEvcil/grokplayer-extension)

Current build: **0.2.56** (`com.grokplayer.tv`, minSdk 24, targetSdk 35). Turkish UI.

## Features

### Home
- Continue-watching hero from in-progress VOD
- Resume or play from start
- Recently opened row with progress bars
- Empty state until a local file, USB, or stream is opened

### Videolar
- Scans internal storage and USB
- Add extra folders from internal or USB
- Filters: all, internal, USB
- Sort: recently added, oldest, A–Z, newest, episode order
- Search across videos, streams, and settings
- Card thumbnails, hover preview, duration, progress bar
- Hold OK for options: play, details, add to playlist/collection, mark watched, like/dislike, send to PC

### Listeler
- **Playlists:** custom lists on the TV, plus PC shared folders when a PC is paired
- **Collections:** auto-grouped related videos (offline from the device, online from a PC playlist)
- Create / rename / delete collections; move a video between them
- Reset collections (all, or keep custom ones)
- Play all / download all
- Last-watched resume bar on a playlist or collection (Devam et / Baştan oynat)
- Watched-episode counts on collection cards
- Progress bars on video tiles (same as Videolar)

### Watch tracking
- Shared VOD state: unwatched / watching / watched
- Progress bar on cards (not on live streams)
- Manual mark watched or unwatched
- Like / dislike saved in `watch.json` (for later recommendations)
- Same video in several playlists or collections shares one watch state (origin URL, else local file)
- Finished = ≥90% or ≤15s remaining
- Manual unwatched stays until playback passes 1s

### Akışlar
- Save VOD or live URLs
- Scan a web page for playable HLS / DASH / progressive media
- YouTube VOD resolve (watch URL, captions, dubbed audio)
- Live badge from the item, not the format string
- Hold OK to play, download (VOD), add to a list, or send to PC
- Progress on VOD tiles only

### İndirilenler
- Queue, progress, retry, cancel, delete
- Deduped jobs; identity is the download id folder + `meta.json`
- Downloaded VOD keeps a human title
- Play from the completed file; watch state shared with the origin URL

### Player
- Overlay player; Back closes and restores last focus
- Queue with previous / next, next-up, auto-next
- Resume offer on VOD (settings)
- Seek with frame previews
- Speed, subtitle, and audio/dub menus
- YouTube captions and dubbed audio tracks
- Live: go-to-live, live edge, no watch-progress chrome
- AVI via libVLC; other VOD via ExoPlayer (HLS / DASH / progressive)

### Cihazlar (LAN)
- Pair with GrokPlayer on the PC (`10.0.2.2` from the emulator)
- Browse shared PC folders as playlists
- Play PC files over `/v1/file` without copying first
- Send a VOD from TV to PC
- Transfers list

### Settings
- Playback: resume, auto-next, seek step, default speed, hide-controls timeout, start screen
- Picture: fit mode, max height
- Audio: preferred language, stereo-only
- Captions: default on, language, size
- Downloads: quality, folder, free space
- Devices: paired PCs
- About: version, package, device, Android

### Remote and focus
- D-pad 10-foot navigation
- OK plays; hold OK opens options in one sheet (details / add / mark — Back returns to the previous page, no stacked modals)
- IME: first Back hides the keyboard, later Back leaves the name page
- Last focused row restored after overlays

## Requirements

- Android Studio Ladybug+ or JDK 17/21 (Android Studio JBR works)
- Android SDK 35, an Android TV / Google TV device or emulator (1080p)

## Build and install

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat installDebug
```

The Television_1080p AVD on `emulator-5554` is the first-stage target. Do not `pm clear` after install (that wipes library and watch data).

## Tests

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat connectedDebugAndroidTest
```

## Not in this tree

Samsung Tizen and LG webOS. DRM (Netflix, Disney+, Widevine) is out of scope.
