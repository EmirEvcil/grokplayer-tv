# GrokPlayer TV

Android TV / Google TV client for the GrokPlayer ecosystem. Leanback shell in Kotlin + Jetpack Compose. Overlay player (ExoPlayer + libVLC for AVI). LAN-only link to the Windows app.

Companion apps: [GrokPlayer (Windows)](https://github.com/EmirEvcil/grokplayer) · [Chrome extension](https://github.com/EmirEvcil/grokplayer-extension)

Current build: **0.2.64** (`com.grokplayer.tv`, minSdk 24, targetSdk 35). Turkish UI.

## Features

### Home
- Continue-watching hero from in-progress VOD
- **Devam et** resumes without asking; **Baştan oynat** starts at 0
- Recently opened row with progress bars
- Empty state until a local file, USB, or stream is opened

### Videolar
- Scans internal storage and USB
- Add extra folders from internal or USB
- Filters: all, internal, USB
- Sort: recently added, oldest, A–Z, newest, episode order
- Search across videos, streams, and settings
- Card thumbnails, hover preview, duration, progress bar
- Hold OK for options: play, details, add to playlist/collection, mark watched, like/dislike, send to PC (rows have icons)

### Listeler
- **Playlists:** custom lists on the TV, plus PC shared folders when a PC is paired
- **Collections:** auto-grouped related videos (offline from the device, online from a PC playlist)
- Collection poster titles overlay two lines with ellipsis
- Create / rename / delete collections; move a video between them
- Reset collections (all, or keep custom ones)
- Play all / download all
- Last-watched resume bar on a playlist or collection (**Devam et** auto-resumes; card/menu play still asks)
- Watched-episode counts on collection cards
- Progress bars on video tiles (same as Videolar)

### Watch tracking
- Shared VOD state: unwatched / watching / watched, stored in `filesDir/watch.json`
- Progress bar on cards (not on live streams)
- Manual mark watched or unwatched
- Like / dislike saved for later recommendations
- Same video in several playlists or collections shares one watch state
- Identity: `youtube:VIDEO_ID` for YouTube, else origin URL without tracking query, else `name|size`
- Finished = ≥90% watched, or ≤15s remaining only when duration is ≥30s
- Manual unwatched stays until playback passes 1s
- Watched counters use the player’s duration when the library entry has none

### Akışlar
- Save VOD or live URLs
- Scan a web page for playable HLS / DASH / progressive media
- YouTube VOD resolve (watch URL, captions, dubbed audio)
- Live vs VOD from the item (`item.isLive`), including YouTube `/live/` and simulcast-style URLs
- Live badge from the item, not the format string
- Sample catalog is seeded once; deleting a stream does not bring it back
- Hold OK to play, download (VOD), add to a list, favorite, or send to PC
- Progress on VOD tiles only

### İndirilenler
- Queue, progress, retry, cancel, delete
- Deduped jobs; identity is the download id folder + `meta.json`
- Downloaded VOD keeps a human title
- Play from the completed file; watch state shared with the origin URL
- YouTube / HLS VOD is saved as a local playlist (init segment + fragments, default audio plus other dubs, captions)
- Duration is stored from the playlist (`#EXTINF`) and shown on the card and player
- Unplayable leftover files (old concatenated fragments) are marked failed so they can be retried
- Download quality in settings (720p / 480p / best)

### Player
- Overlay player; Back closes and restores last focus
- Queue with previous / next, next-up, auto-next (finished items are marked watched)
- In-player resume popup (**Kaldığın yer**) on VOD when opened from a card or menu; uses the real duration
- Seek with frame previews: a window around the playhead is preloaded on local/progressive files; capture after seek is the fallback (including local HLS)
- Speed, subtitle, and audio/dub menus (icons on every row)
- Selected speed is applied again when the decoder is ready
- YouTube captions (overlay + sidecar VTT) and dubbed audio tracks
- Live: seek in the window, go-to-live, live edge, no watch-progress chrome
- AVI via libVLC; other VOD via ExoPlayer (HLS / DASH / progressive / local HLS)
- A completed download is opened from the local file, not re-resolved from YouTube

### Cihazlar (LAN)
- Pair with GrokPlayer on the PC (`10.0.2.2` from the emulator)
- Browse shared PC folders as playlists
- Play PC files over `/v1/file` without copying first
- Send a VOD from TV to PC
- Share folders from the TV
- Transfers list

### Settings
- Playback: resume, auto-next, seek step, default speed, hide-controls timeout, start screen
- Picture: fit mode, max height
- Audio: preferred language, stereo-only
- Captions: default on, language, size
- Downloads: quality, folder, free space
- Devices: paired PCs, transfers
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
```

Do not run `connectedDebugAndroidTest` against a device you care about: it reinstalls the APK and clears app data.

## Not in this tree

Samsung Tizen and LG webOS. DRM (Netflix, Disney+, Widevine) is out of scope.
