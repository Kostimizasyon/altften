# Altften Recorder 🎙️⌚

A smart, battery-conscious rolling voice recorder application engineered for **Wear OS** smartwatches (Samsung Galaxy Watch 4, 5, 6, 7, and Ultra).

Altften continuously records your surroundings in the background and keeps a rolling buffer of audio in **RAM** instead of writing constantly to flash storage. When something important happens, a single tap permanently saves the last $X$ chunks as separate `.wav` files.

---

## 💡 The Core Problem & Philosophy

Continuous recording on smartwatches normally drains the battery in a few hours and wears out flash storage due to constant disk I/O. 

**Altften solves this with an in-memory RAM buffer architecture:**
* **Zero Flash I/O During Normal Recording**: Audio is kept entirely in RAM. The watch flash storage is never touched until you explicitly press **"SAVE LAST X CHUNKS"**.
* **Ultra-Low Memory Footprint**: Even 6 minutes of 16 kHz 16-bit mono voice audio uses **only ~11.5 MB of RAM**—a tiny fraction of a Galaxy Watch's 1.5–2 GB RAM.
* **Continuous Audio Stream (Zero Dropouts)**: Slicing occurs in the raw PCM stream in memory without restarting `AudioRecord`, avoiding the 100–300 ms dead spots typical of `MediaRecorder`.

---

## ✨ Features

* **3-Slot Clamped Rotation (`chunk1` $\rightarrow$ `chunk2` $\rightarrow$ `chunk3` $\rightarrow$ `chunk1`)**:
  Rolling chunks are held in slots. When `chunk3` finishes, the slot counter clamps back to `1` and overwrites `chunk1` in RAM.
* **Mid-Chunk Save & Instant Advance**:
  If you tap Save at 1 minute and 15 seconds into a 2-minute chunk, the engine immediately finalizes that 1m 15s chunk, writes the requested chunks to disk, and starts the next slot at 00:00 without dropping a single millisecond of microphone capture.
* **Separate Files with Timestamps**:
  Saved chunks are exported as individual `.wav` files named with the exact timestamp and slot (e.g., `saved_20261101_153000_chunk2_120s.wav` and `saved_20261101_153000_chunk3_75s.wav`) in persistent storage so they are never overwritten.
* **Tactile Haptic Feedback**:
  * **Save Success**: Distinct double buzz (`80ms buzz, 70ms pause, 140ms strong buzz`) so you know your audio was captured without looking at your wrist.
  * **Start Session**: Crisp single-click vibration.
  * **Stop Session**: Soft double tap.
* **Tunable Quality & Chunk Duration**:
  * **Low (Saver)**: 16 kHz Mono • ~3.8 MB / 2 min chunk • Lowest CPU & RAM usage.
  * **Medium**: 24 kHz Mono • ~5.7 MB / 2 min chunk • Balanced voice clarity.
  * **High**: 44.1 kHz Mono • ~10.5 MB / 2 min chunk • Studio speech clarity.
  * **Chunk Durations**: 30s, 60s, or 120s (max 2 minutes).
  * **Chunks to Save**: 1, 2, or 3 chunks.
* **AMOLED Dark Theme**: Jetpack Compose Wear UI with pure black background to preserve battery life on watch OLED displays.
* **Foreground Service with WakeLock**: Keeps recording reliably even when the watch screen dims, sleeps, or enters ambient mode.

---

## 🏗️ Architecture & Project Structure

```
altften/
├── app/
│   ├── src/main/
│   │   ├── AndroidManifest.xml               # Wear OS features, permissions (RECORD_AUDIO, VIBRATE, WAKE_LOCK)
│   │   ├── java/com/altften/recorder/
│   │   │   ├── MainActivity.kt               # Wear OS entry point & runtime permissions
│   │   │   ├── data/
│   │   │   │   ├── AudioConfig.kt            # Quality presets, chunk lengths & capacities
│   │   │   │   └── AudioChunk.kt             # In-memory chunk representation
│   │   │   ├── audio/
│   │   │   │   ├── RollingRamBuffer.kt       # 3-slot rolling RAM buffer & .wav exporter
│   │   │   │   ├── AudioRecorderEngine.kt    # Continuous AudioRecord reader & mid-chunk slicer
│   │   │   │   └── WavWriter.kt              # Standard 44-byte RIFF WAV generator
│   │   │   ├── service/
│   │   │   │   └── RecordingService.kt       # Foreground Service, notifications & WakeLock
│   │   │   ├── util/
│   │   │   │   └── HapticHelper.kt           # Watch vibration patterns
│   │   │   └── presentation/
│   │   │       ├── theme/                    # AMOLED Wear OS color palette & theme
│   │   │       └── MainScreen.kt             # Jetpack Compose Wear OS round screen UI
│   │   └── res/                              # Drawables, mipmaps, strings, colors
│   └── build.gradle.kts                      # App module build file
├── gradle/
│   └── libs.versions.toml                    # Modern Gradle Version Catalog
├── project-structure.md                      # Detailed technical documentation for agents & devs
└── TODO.md                                   # Development roadmap & user backlog
```

For detailed class diagrams and technical responsibilities, see [`project-structure.md`](file:///c:/Users/kapla/StudioProjects/altften/project-structure.md).

---

## 🛠️ Requirements & Getting Started

### Prerequisites
* **Android Studio** (Koala / Ladybug or newer recommended).
* **JDK 17**.
* **Physical Device**: Samsung Galaxy Watch 4, 5, 6, 7, or Watch Ultra running **Wear OS 3.0+** (Android API 30+).

### Enabling Wireless Debugging on Galaxy Watch
1. On your watch, navigate to **Settings > About watch > Software info**.
2. Tap **Software version** 7 times until you see the toast *"Developer mode turned on"*.
3. Go back to **Settings > Developer options**.
4. Enable **ADB debugging** and **Wireless debugging**.
5. Note the IP address and port shown under Wireless debugging.
6. From your computer terminal, connect via ADB:
   ```bash
   adb connect <WATCH_IP>:<PORT>
   ```
7. Accept the debugging prompt on your watch screen.

### Building & Running
Open the project in Android Studio or build via the terminal:

```bash
# Build debug APK
./gradlew assembleDebug

# Install directly to connected watch
./gradlew installDebug
```

---

## 🔋 Recommended Usage for Optimal Battery Life

While Altften is aggressively optimized by eliminating flash I/O and running low-bitrate mono audio:
* Use Altften as an **on-demand session recorder** (e.g., activate during meetings, interviews, classes, or important conversations).
* For longest endurance, use the **Low (Saver - 16kHz)** audio profile.
* Stop the session when your meeting ends to allow the watch CPU to return to deep sleep.

---

## 📄 License
Open source under the [MIT License](LICENSE).
