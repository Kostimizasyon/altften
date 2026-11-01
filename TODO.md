# Altften Recorder - Project Roadmap & Task Tracker

This document tracks development phases, completed tasks, in-progress work, and provides a backlog section where you can append upcoming tasks or feature requests.

---

## 📌 Phase 1: Core Architecture & In-Memory Pipeline (Completed ✅)
- [x] Configure Wear OS Kotlin project with Gradle Version Catalog (`libs.versions.toml`).
- [x] Declare required Wear OS hardware features, ambient mode, microphone foreground service, and `VIBRATE` permissions in `AndroidManifest.xml`.
- [x] Implement `AudioConfig` data structures supporting:
  - Quality presets: `LOW` (16kHz), `MEDIUM` (24kHz), `HIGH` (44.1kHz).
  - Chunk lengths: 30s, 60s, 120s (upper limit 2 mins).
  - Configurable rolling capacity (default 3 chunks) and save count.
- [x] Build `RollingRamBuffer`:
  - Clamped 3-slot rotation: `chunk1 -> chunk2 -> chunk3 -> chunk1`.
  - Overwrites current slot in RAM on wrap.
  - Chronological history tracking.
- [x] Build `WavWriter`:
  - Generates standard 44-byte RIFF header.
  - Writes individual, separate `.wav` files for each chunk on export.
- [x] Build `AudioRecorderEngine`:
  - Low-level continuous `AudioRecord` background thread.
  - Mid-chunk cutting: flushes accumulated audio (e.g. 1m 15s) into current slot immediately when Save is triggered.
  - Advances to next slot seamlessly with zero dropped audio samples.
- [x] Build `HapticHelper`:
  - Distinct tactile double-buzz `[80ms buzz, 70ms pause, 140ms strong buzz]` on successful save.
  - Crisp single-click buzz on recording start.
  - Soft double tap on recording stop.
- [x] Build `RecordingService`:
  - Foreground service with `android:foregroundServiceType="microphone"`.
  - Holds `PARTIAL_WAKE_LOCK` to ensure continuous recording with screen off.
  - Ongoing notification with quick "Save Last Chunks" and "Stop" actions.
- [x] Build Wear OS Jetpack Compose UI (`MainScreen.kt`):
  - AMOLED dark theme.
  - Live buffer status display (elapsed chunk time, active slot 1..3, estimated RAM used).
  - Prominent "SAVE LAST X CHUNKS" button.
  - Audio quality, chunk length, and chunk count selector chips.
  - Dynamic result banner showing saved chunk names and sizes.
- [x] Create and maintain `project-structure.md` and `TODO.md`.

---

## 🚀 Phase 2: Wear OS Polish & Optimizations (Next Up)
- [ ] **Ambient Mode Support**: Implement `AmbientLifecycleObserver` so the screen can enter low-power ambient mode without freezing the timer.
- [ ] **Rotary Input Support**: Hook up the physical watch rotating crown / bezel to scroll settings.
- [ ] **Saved Recordings List Screen**: A simple screen on the watch to see saved `.wav` files, check their file sizes, and play back or delete them.

---

## 🌐 Phase 3: Phone Sync & Export (Future)
- [ ] **Wearable Data Layer Sync**: Stream saved `.wav` files over Bluetooth/Wi-Fi to the paired Android phone companion app using `ChannelClient`.
- [ ] **Audio Compression on Export**: Optional Opus or AAC encoding when saving to flash to reduce file size before sync.
- [ ] **Auto-Cleanup**: Setting to auto-delete local recordings from the watch once successfully synced to the phone.

---

## 📝 User Task Backlog (Add Your Requests Below)
*You can edit this section or tell the agent what to add here!*

1. [ ] *(Add your new task or requirement here)*
