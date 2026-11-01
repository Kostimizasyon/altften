package com.altften.recorder.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Text
import com.altften.recorder.data.AudioQuality
import com.altften.recorder.data.RecordingConfig
import com.altften.recorder.presentation.theme.GreenSuccess
import com.altften.recorder.presentation.theme.OrangePrimary
import com.altften.recorder.presentation.theme.RedStop
import com.altften.recorder.service.RecordingService
import kotlinx.coroutines.flow.collectLatest

@Composable
fun MainScreen(
    onStartRecording: (RecordingConfig) -> Unit,
    onStopRecording: () -> Unit,
    onSaveLastChunks: (Int) -> Unit
) {
    val isRunning by RecordingService.isServiceRunning.collectAsStateWithLifecycle()
    val progress by RecordingService.serviceProgress.collectAsStateWithLifecycle()

    var selectedQuality by remember { mutableStateOf(AudioQuality.LOW) }
    var chunkDurationSec by remember { mutableStateOf(120) } // Max 2 mins
    var chunksToSaveCount by remember { mutableStateOf(2) }
    var lastSavedMessage by remember { mutableStateOf<String?>(null) }

    val listState = rememberScalingLazyListState()

    LaunchedEffect(Unit) {
        RecordingService.saveEvents.collectLatest { result ->
            when (result) {
                is RecordingService.SaveResult.Success -> {
                    val count = result.fileNames.size
                    val mb = String.format("%.1f", result.totalSizeBytes / (1024f * 1024f))
                    val names = result.fileNames.joinToString(", ") { name ->
                        name.substringAfter("chunk").substringBefore(".wav").let { "chunk$it" }
                    }
                    lastSavedMessage = "Saved $count ($mb MB): $names"
                }
                is RecordingService.SaveResult.Error -> {
                    lastSavedMessage = "Error: ${result.message}"
                }
            }
        }
    }

    ScalingLazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        state = listState,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        item {
            Text(
                text = "Altften",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = OrangePrimary
            )
        }

        if (isRunning) {
            // Live Status Card
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val minutes = progress.currentChunkElapsedSec / 60
                    val seconds = progress.currentChunkElapsedSec % 60
                    val targetMinutes = progress.targetChunkDurationSec / 60
                    val targetSeconds = progress.targetChunkDurationSec % 60

                    Text(
                        text = String.format("%02d:%02d / %02d:%02d", minutes, seconds, targetMinutes, targetSeconds),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    Text(
                        text = "Recording chunk${progress.currentSlotIndex} (1..3)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = OrangePrimary
                    )

                    val ramMb = progress.ramUsedBytes / (1024f * 1024f)
                    Text(
                        text = "Buffer: ${progress.totalChunksInBuffer}/3 in RAM (${String.format("%.1f", ramMb)} MB)",
                        fontSize = 10.sp,
                        color = Color.Gray
                    )
                }
            }

            // Prominent Save Button
            item {
                Button(
                    onClick = { onSaveLastChunks(chunksToSaveCount) },
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .height(52.dp),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = GreenSuccess,
                        contentColor = Color.Black
                    )
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "SAVE LAST $chunksToSaveCount CHUNKS",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "Preserves separate files",
                            fontSize = 9.sp
                        )
                    }
                }
            }

            // Stop Session Button
            item {
                Button(
                    onClick = onStopRecording,
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .height(38.dp),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = RedStop,
                        contentColor = Color.White
                    )
                ) {
                    Text(text = "Stop Session", fontSize = 12.sp)
                }
            }
        } else {
            // Not Recording - Setup & Start
            item {
                Button(
                    onClick = {
                        val config = RecordingConfig(
                            quality = selectedQuality,
                            chunkDurationSec = chunkDurationSec,
                            bufferCapacity = 3,
                            chunksToSave = chunksToSaveCount
                        )
                        onStartRecording(config)
                    },
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = OrangePrimary,
                        contentColor = Color.White
                    )
                ) {
                    Text(
                        text = "Start Session",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Audio Quality Selection
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Audio Quality",
                    fontSize = 11.sp,
                    color = Color.Gray
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(0.9f),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    AudioQuality.values().forEach { quality ->
                        val isSelected = quality == selectedQuality
                        Chip(
                            modifier = Modifier.weight(1f),
                            onClick = { selectedQuality = quality },
                            label = {
                                Text(
                                    text = quality.label.split(" ").first(),
                                    fontSize = 10.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            },
                            colors = ChipDefaults.chipColors(
                                backgroundColor = if (isSelected) OrangePrimary else Color(0xFF26262B),
                                contentColor = Color.White
                            )
                        )
                    }
                }
            }

            // Chunk Duration Selection (Max 2 mins / 120s)
            item {
                Text(
                    text = "Chunk Length (Max 2 min)",
                    fontSize = 11.sp,
                    color = Color.Gray
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(0.9f),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf(30, 60, 120).forEach { sec ->
                        val isSelected = sec == chunkDurationSec
                        val label = if (sec >= 60) "${sec / 60}m" else "${sec}s"
                        Chip(
                            modifier = Modifier.weight(1f),
                            onClick = { chunkDurationSec = sec },
                            label = {
                                Text(
                                    text = label,
                                    fontSize = 10.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            },
                            colors = ChipDefaults.chipColors(
                                backgroundColor = if (isSelected) OrangePrimary else Color(0xFF26262B),
                                contentColor = Color.White
                            )
                        )
                    }
                }
            }

            // Number of Chunks to Save (1, 2, or 3)
            item {
                Text(
                    text = "Save Last X Chunks",
                    fontSize = 11.sp,
                    color = Color.Gray
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(0.9f),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf(1, 2, 3).forEach { count ->
                        val isSelected = count == chunksToSaveCount
                        Chip(
                            modifier = Modifier.weight(1f),
                            onClick = { chunksToSaveCount = count },
                            label = {
                                Text(
                                    text = "$count",
                                    fontSize = 11.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            },
                            colors = ChipDefaults.chipColors(
                                backgroundColor = if (isSelected) GreenSuccess else Color(0xFF26262B),
                                contentColor = if (isSelected) Color.Black else Color.White
                            )
                        )
                    }
                }
            }
        }

        // Notification / Feedback Banner
        lastSavedMessage?.let { msg ->
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = msg,
                    fontSize = 10.sp,
                    color = GreenSuccess,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }
        }
    }
}
