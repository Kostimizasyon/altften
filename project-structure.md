# Altften Recorder - Project Structure & Architecture

Altften is a Wear OS (Samsung Galaxy Watch 4/5/6/7/Ultra) continuous audio recording application utilizing an in-memory (RAM-only) rolling circular buffer. 

## Architectural Philosophy

Continuous audio recording on smartwatches quickly drains battery if unoptimized:
1. **Zero Flash I/O During Recording**:
   Writing audio to flash storage every 2 minutes wastes battery and wears out watch flash storage. Instead, Altften captures raw PCM in RAM. Only when the user taps "Save Last Chunks" are the bytes converted and written to persistent storage as separate `.wav` files.
2. **Clamped Slot Rotation (chunk1 -> chunk2 -> chunk3 -> chunk1)**:
   - The rolling buffer operates across 3 slots: `chunk1`, `chunk2`, `chunk3`.
   - When `chunk3` finishes, the slot counter clamps back to `1` and overwrites `chunk1` in RAM.
3. **Mid-Chunk Save & Instant Advance**:
   - If the user taps "Save Last X Chunks" mid-chunk (e.g., at 1m 15s into a 2m chunk), the engine immediately cuts the accumulated 1m 15s audio as that chunk's slot.
   - The requested last $X$ chunks are immediately preserved to storage.
   - The next slot is started immediately at 00:00 without stopping `AudioRecord` or dropping any audio samples.
4. **Separate Chunk Files on Save (No Overwrite)**:
   - Chunks are saved as **individual, separate `.wav` files** (not stitched into a single file).
   - Each file is saved with the timestamp and slot name:
     `saved_20261031_170530_chunk2_120s.wav`
     `saved_20261031_170530_chunk3_75s.wav`
   - Because they are saved with unique timestamps into persistent storage, future rolling chunk rotations in RAM will never overwrite them.
5. **Tactile Haptic Feedback**:
   - Built-in `HapticHelper` provides distinct wrist vibrations for Galaxy Watch:
     - **Save Success**: Double-buzz pattern `[80ms buzz, 70ms pause, 140ms strong buzz]`.
     - **Start**: Crisp single click.
     - **Stop**: Soft double tap.
6. **Tunable Audio Fidelity vs. Memory Footprint**:
   - **Low (Battery Saver)**: 16 kHz Mono, 16-bit PCM (~32 KB/sec). 3 chunks of 2 min = ~11.5 MB RAM total.
   - **Medium (Balanced)**: 24 kHz Mono, 16-bit PCM (~48 KB/sec). 3 chunks of 2 min = ~17.3 MB RAM total.
   - **High (Clarity)**: 44.1 kHz Mono, 16-bit PCM (~88.2 KB/sec). 3 chunks of 2 min = ~31.7 MB RAM total.

---

## Directory & File Layout

```
altften/
├── app/
│   ├── build.gradle.kts                          # App-level build config, dependencies (Wear Compose, Coroutines)
│   ├── proguard-rules.pro                        # Minification & ProGuard rules
│   └── src/
│       └── main/
│           ├── AndroidManifest.xml               # Permissions (RECORD_AUDIO, FOREGROUND_SERVICE, VIBRATE, WAKE_LOCK)
│           ├── java/com/altften/recorder/
│           │   ├── MainActivity.kt               # Wear OS entry point, permission requests, launches Compose UI
│           │   ├── data/
│           │   │   ├── AudioConfig.kt            # AudioQuality enums, RecordingConfig (durations, buffer size, save count)
│           │   │   └── AudioChunk.kt             # Data class holding a completed chunk (slot 1..3, pcmData, duration)
│           │   ├── audio/
│           │   │   ├── RollingRamBuffer.kt       # 3-slot rolling RAM buffer, clamped eviction & separate .wav exporter
│           │   │   ├── AudioRecorderEngine.kt    # Continuous AudioRecord reader, mid-chunk cutter, clamped slot rotation
│           │   │   └── WavWriter.kt              # Generates standard 44-byte RIFF WAV headers for individual chunks
│           │   ├── service/
│           │   │   └── RecordingService.kt       # Foreground Service, notifications with Quick Actions, WakeLock, haptic triggers
│           │   ├── util/
│           │   │   └── HapticHelper.kt           # Watch vibration patterns (Save success double buzz, start/stop pulses)
│           │   └── presentation/
│           │       ├── theme/
│           │       │   ├── Color.kt              # AMOLED-friendly high-contrast dark color palette
│           │       │   └── Theme.kt              # Wear OS MaterialTheme wrapper
│           │       └── MainScreen.kt             # Wear OS Jetpack Compose round UI: controls, live status, prominent Save button
│           └── res/
│               ├── values/
│               │   ├── strings.xml               # App & notification string resources
│               │   └── colors.xml                # Color tokens
│               └── mipmap-anydpi-v26/
│                   └── ic_launcher.xml           # Watch app launcher icon
├── gradle/
│   ├── libs.versions.toml                        # Version catalog (AGP, Kotlin, Wear Compose, etc.)
│   └── wrapper/
│       └── gradle-wrapper.properties             # Gradle 8.9 distribution config
├── build.gradle.kts                              # Root build file
├── settings.gradle.kts                           # Project & repository settings
├── gradle.properties                             # JVM args, AndroidX flags
├── project-structure.md                          # Architecture & file guide (this file)
└── TODO.md                                       # Project roadmap, task tracker & user backlog
```

---

## Component Roles & Interaction Flow

```
[ Microphone ]
      │
      ▼
[ AudioRecorderEngine ] ── (Collects PCM stream in RAM)
      │
      ├── Reaches chunk duration OR user presses "SAVE"
      ▼
[ cutCurrentChunkAndAdvance() ]
      │
      ▼
[ RollingRamBuffer ] ── (Stores into slot 1, 2, or 3. Wraps 3 -> 1)
      │
      ▼ (When Save Last X Chunks triggered)
[ saveLastChunksToDisk() ]
      │
      ├── Writes separate files: saved_TIMESTAMP_chunkX_DURATIONs.wav
      ▼
[ HapticHelper ] ── (Fires double-buzz wrist vibration)
```
