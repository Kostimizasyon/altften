package com.altften.recorder.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.altften.recorder.MainActivity
import com.altften.recorder.R
import com.altften.recorder.audio.AudioRecorderEngine
import com.altften.recorder.audio.EngineProgress
import com.altften.recorder.audio.RollingRamBuffer
import com.altften.recorder.data.AudioQuality
import com.altften.recorder.data.RecordingConfig
import com.altften.recorder.util.HapticHelper
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

class RecordingService : LifecycleService() {

    private var wakeLock: PowerManager.WakeLock? = null
    private lateinit var ramBuffer: RollingRamBuffer
    private lateinit var engine: AudioRecorderEngine

    override fun onCreate() {
        super.onCreate()
        ramBuffer = RollingRamBuffer(capacity = 3)
        engine = AudioRecorderEngine(ramBuffer, lifecycleScope)

        lifecycleScope.launch {
            engine.progress.collect { progress ->
                _serviceProgress.value = progress
                updateNotification(progress)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        val action = intent?.action ?: return START_NOT_STICKY

        when (action) {
            ACTION_START -> {
                val qualityName = intent.getStringExtra(EXTRA_QUALITY) ?: AudioQuality.LOW.name
                val chunkDuration = intent.getIntExtra(EXTRA_CHUNK_DURATION, 120)
                val bufferCap = intent.getIntExtra(EXTRA_BUFFER_CAPACITY, 3)
                val saveCount = intent.getIntExtra(EXTRA_CHUNKS_TO_SAVE, 2)

                val config = RecordingConfig(
                    quality = AudioQuality.valueOf(qualityName),
                    chunkDurationSec = chunkDuration,
                    bufferCapacity = bufferCap,
                    chunksToSave = saveCount
                )
                startRecordingSession(config)
            }
            ACTION_STOP -> {
                stopRecordingSession()
            }
            ACTION_SAVE_LAST -> {
                val count = intent.getIntExtra(EXTRA_CHUNKS_TO_SAVE, activeConfig.chunksToSave)
                saveLastChunks(count)
            }
        }
        return START_STICKY
    }

    private fun startRecordingSession(config: RecordingConfig) {
        activeConfig = config
        acquireWakeLock()
        createNotificationChannel()

        val notification = buildNotification(EngineProgress(isRecording = true, currentSlotIndex = 1))
        startForeground(NOTIFICATION_ID, notification)

        engine.start(config)
        _isServiceRunning.value = true
        HapticHelper.vibrateStart(this)
    }

    private fun stopRecordingSession() {
        engine.stop()
        releaseWakeLock()
        _isServiceRunning.value = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        HapticHelper.vibrateStop(this)
        stopSelf()
    }

    private fun saveLastChunks(count: Int) {
        // 1. Cut the mid-chunk immediately so whatever was recorded (e.g. 1m 15s) is captured
        engine.cutCurrentChunkAndAdvance()

        // 2. Persistent recordings directory
        val destDir = getExternalFilesDir(Environment.DIRECTORY_RECORDINGS)
            ?: File(filesDir, "recordings")

        // 3. Save each requested chunk as an individual timestamped .wav file
        val result = ramBuffer.saveLastChunksToDisk(count = count, outputDir = destDir)
        result.onSuccess { savedFiles ->
            // Trigger tactile wrist vibration
            HapticHelper.vibrateSaveSuccess(this)

            val fileNames = savedFiles.map { it.name }
            val totalBytes = savedFiles.sumOf { it.length() }
            val totalMb = totalBytes / (1024f * 1024f)

            val summaryMsg = "Saved ${savedFiles.size} chunks (${String.format("%.1f", totalMb)}MB)"
            lifecycleScope.launch {
                _saveEvents.emit(SaveResult.Success(fileNames, totalBytes))
            }
            Toast.makeText(this, summaryMsg, Toast.LENGTH_LONG).show()
        }.onFailure { err ->
            lifecycleScope.launch {
                _saveEvents.emit(SaveResult.Error(err.message ?: "Failed to save"))
            }
            Toast.makeText(this, "Error saving: ${err.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "Altften::RecordingWakeLock"
            ).apply {
                setReferenceCounted(false)
                acquire(4 * 60 * 60 * 1000L) // 4 hours safety timeout
            }
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing wakeLock", e)
        }
        wakeLock = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(progress: EngineProgress): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            this, 0, openIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val saveIntent = Intent(this, RecordingService::class.java).apply {
            action = ACTION_SAVE_LAST
            putExtra(EXTRA_CHUNKS_TO_SAVE, activeConfig.chunksToSave)
        }
        val savePendingIntent = PendingIntent.getService(
            this, 1, saveIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, RecordingService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 2, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val subtext = "Chunk ${progress.currentSlotIndex} (in RAM: ${progress.totalChunksInBuffer}/${activeConfig.bufferCapacity})"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Altften: Active (chunk${progress.currentSlotIndex})")
            .setContentText(subtext)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setContentIntent(openPendingIntent)
            .addAction(R.mipmap.ic_launcher, getString(R.string.action_save), savePendingIntent)
            .addAction(R.mipmap.ic_launcher, getString(R.string.action_stop), stopPendingIntent)
            .build()
    }

    private fun updateNotification(progress: EngineProgress) {
        if (!_isServiceRunning.value) return
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(progress))
    }

    override fun onDestroy() {
        stopRecordingSession()
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    sealed class SaveResult {
        data class Success(val fileNames: List<String>, val totalSizeBytes: Long) : SaveResult()
        data class Error(val message: String) : SaveResult()
    }

    companion object {
        private const val TAG = "RecordingService"
        private const val CHANNEL_ID = "altften_recording_channel"
        private const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.altften.recorder.ACTION_START"
        const val ACTION_STOP = "com.altften.recorder.ACTION_STOP"
        const val ACTION_SAVE_LAST = "com.altften.recorder.ACTION_SAVE_LAST"

        const val EXTRA_QUALITY = "extra_quality"
        const val EXTRA_CHUNK_DURATION = "extra_chunk_duration"
        const val EXTRA_BUFFER_CAPACITY = "extra_buffer_capacity"
        const val EXTRA_CHUNKS_TO_SAVE = "extra_chunks_to_save"

        var activeConfig = RecordingConfig()

        private val _isServiceRunning = MutableStateFlow(false)
        val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

        private val _serviceProgress = MutableStateFlow(EngineProgress())
        val serviceProgress: StateFlow<EngineProgress> = _serviceProgress.asStateFlow()

        private val _saveEvents = MutableSharedFlow<SaveResult>()
        val saveEvents: SharedFlow<SaveResult> = _saveEvents.asSharedFlow()

        fun startService(context: Context, config: RecordingConfig) {
            val intent = Intent(context, RecordingService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_QUALITY, config.quality.name)
                putExtra(EXTRA_CHUNK_DURATION, config.chunkDurationSec)
                putExtra(EXTRA_BUFFER_CAPACITY, config.bufferCapacity)
                putExtra(EXTRA_CHUNKS_TO_SAVE, config.chunksToSave)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, RecordingService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        fun saveLast(context: Context, count: Int = activeConfig.chunksToSave) {
            val intent = Intent(context, RecordingService::class.java).apply {
                action = ACTION_SAVE_LAST
                putExtra(EXTRA_CHUNKS_TO_SAVE, count)
            }
            context.startService(intent)
        }
    }
}
