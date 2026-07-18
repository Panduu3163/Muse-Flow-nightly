<div align="center">

# 🎵 MuseFlow

**A free, ad-free music streaming app for Android.**

Built with Kotlin, Jetpack Compose, and Media3 — a personal project aiming for a free premium music experience without the price tag.

![Kotlin](https://img.shields.io/badge/Kotlin-100%25-7F52FF?style=for-the-badge&logo=kotlin)
![Platform](https://img.shields.io/badge/Platform-Android-3DDC84?style=for-the-badge&logo=android)
![License](https://img.shields.io/badge/License-GPL--3.0-blue?style=for-the-badge)
![Status](https://img.shields.io/badge/Status-v1.0.9%20Beta-orange?style=for-the-badge)

</div>

---

## ⚠️ Before you read further

MuseFlow is a **personal hobby project**, not a commercial product. It has bugs. It's actively being worked on. It exists because I wanted to learn and build something I'd actually use — not to compete with anyone.

It relies on unofficial/reverse-engineered access to some music platforms' internal APIs (details below), which exists in a legal gray area regarding those platforms' Terms of Service. This is the same trade-off made by several well-known open-source music apps this project draws inspiration and code from. Use accordingly.

---

## ✨ Features

### 🎧 Playback & Control
- Background playback with a fully controllable media notification (play/pause/skip, cover art).
- Gapless queue management — skip, shuffle, repeat, and an interactive **Up Next** modal directly on the player.
- **Sleep Timer:** Fully functional sleep timer (10, 15, 30, 45 mins) to automatically pause playback.
- **Dynamic Track Sources:** Accurately displays stream origins (e.g., JioSaavn, YouTube Music) and quality details directly on the Now Playing screen.

### 🔍 Discovery & Home
- **Dynamic Home Screen:** Intelligent time-based greetings ("Good morning", "Late night") and rich shelves (Recently Played, genre-based recommendations).
- Search across **Songs, Albums, Artists, and Playlists**, deduplicated across multiple sources.
- **Search History:** Persistent search history with quick "Recent Searches" recall.
- **Offline Home Cache:** Caches Home shelves to a Room database and displays an offline banner when network connectivity is lost, ensuring the app is always functional.

### 🎤 Syllable-Synced Lyrics
- Real-time synced lyrics, scrolling in perfect time with playback.
- **Syllable-Level Highlighting:** Advanced parsing extracts precise millisecond-level timings to highlight individual words as the artist sings them, providing a premium karaoke-like experience.

### 📚 Ultimate Library Management
- **Unified Hub:** Clean, chip-based navigation for Playlists, Liked Songs, Downloads, Local Audio, and Cached Tracks.
- **Local Audio Integration:** Automatically scans your device via `MediaStore` and plays local MP3s alongside streaming tracks, complete with embedded cover art parsing.
- **Smart Playlists:** Create playlists with source-selection features (Auto-fill from Liked Songs, Downloads, or initiate an Online Search).
- **Intelligent Caching:** Efficient 100MB LRU cache for internet-streamed songs, viewable via the "Cached" tab.
- **Downloads:** One-tap track downloading with determinate circular progress indicators and sequenced queue limits to preserve bandwidth. "Download All" option for Liked Songs.
- **Seamless Stream Fallback:** Advanced local database mapping ensures downloaded and liked songs from external providers (like YouTube Music) are seamlessly re-resolved if stream links expire.

### 📊 Advanced Listening Stats
- **Detailed History:** Seamless, chronological history view tracking every played song.
- **Analytics Dashboard:** A robust stats screen dynamically aggregating your Top Songs, Top Artists, and Top Albums. Features precise timeframe filters (1 week, 1 month, 3 months, 6 months, 1 year, continuous).

### 🎨 Personalization & Premium UI
- First-launch onboarding with a custom display name and profile photo.
- AMOLED (true black) and Gradient theme modes, with highly customizable color palettes.
- **Deep UI Polish:**
  - Immersive Now Playing screen with frosted glass backgrounds reflecting the current album art.
  - Automatic marquee scrolling for long titles.
  - Smooth Compose navigation transitions and `Crossfade` image loading via Coil.
  - Smoothly interpolating seekbars.

---

## 🛠️ Tech Stack

| Layer | Technology |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 |
| Playback | Media3 / ExoPlayer |
| Architecture | MVVM, Hilt (DI), Kotlin Coroutines & Flow |
| Local Storage | Room, DataStore Preferences |
| Networking | Retrofit, OkHttp, Moshi |
| Images | Coil |

### How music sourcing works

MuseFlow doesn't host or own any music. It resolves playable audio through a **provider-chain architecture** — multiple independent sources, tried and merged so no single point of failure takes down the app:

- **JioSaavn** — primary catalog source, public API.
- **YouTube Music** — full authenticated streaming pipeline (visitor identity, BotGuard proof-of-origin token generation, signature/cipher deobfuscation) for access to YouTube's much broader catalog. Extracts precise monthly listeners/subscribers counts.
- **LRCLib** — open, public API for synced lyrics.

Each source is isolated behind a shared `Provider` interface, so if one breaks, the others keep the app functional.

---

## 🙏 Credits & Acknowledgements

MuseFlow wouldn't exist without the open-source music-client community. Significant logic, architecture patterns, and research in this project were adapted from:

- [Metrolist](https://github.com/MetrolistGroup/Metrolist) — reference implementation for YouTube Music integration
- [zemer-cipher](https://github.com/ZemerTeam/zemer-cipher) — YouTube cipher deobfuscation and PoToken generation
- [SimpMusic](https://github.com/maxrave-dev/SimpMusic) — cross-reference for YouTube Music streaming
- [Echo Music](https://github.com/EchoMusicApp/Echo-Music) — architectural inspiration (provider-chain/fallback pattern, feature set)
- [LRCLib](https://lrclib.net) — synced lyrics API

Genuine thanks to the maintainers of these projects for their work being open enough to learn from.
Also thanks to the tester **Md Sakib Rahman** for testing and finding bugs.

---

## 📦 Getting the App

Download the latest `museflow_nightly_1.0.9_beta.apk` from the Releases section or build it yourself:

**Prerequisites:** Android Studio (or the Android SDK + JDK 17+ directly), Kotlin 2.2+

1. Clone this repo
2. Open in Android Studio and let it sync
3. Build a debug APK: `./gradlew assembleDebug`
4. Install on your device

---

## 🚧 Roadmap

- [x] Additional lyrics source fallbacks
- [x] Word-by-word synced lyrics
- [x] Artist pages with monthly listener counts
- [x] Continued UI polish
- [x] Reliable cross-source library mapping (YT Music & JioSaavn)
- [ ] Listen Together (real-time synced listening sessions)
- [ ] Cross-device playback sync

---

## 📄 License

This project is licensed under **GPL-3.0**, consistent with the licenses of the upstream projects it adapts code and research from. See [LICENSE](LICENSE) for the full text.

---

## 👤 Developer

**Mynul Kabir Nayem**  
📧 mynulkbr@gmail.com

<div align="center">

*Made with a lot of trial, error, and genuine love for music.*

</div>
