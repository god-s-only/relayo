package com.relayo.feature.voicenotes

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.relayo.domain.model.VoiceNote

@Composable
fun VoiceNotesScreen(
    viewModel:VoiceNotesViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            androidx.core.content.ContextCompat.checkSelfPermission(
                context, Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                text = "Voice Notes",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(20.dp)
            )

            if(uiState.isRecording) {
                Text(
                    text = "Recording — ${uiState.elapsedMillis / 1000}s / 60s",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(uiState.notes, key = { it.id }) { note ->
                    VoiceNoteRow(note = note)
                }
            }
        }

        FloatingActionButton(
            onClick = {
                if(!hasPermission) {
                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                } else if(uiState.isRecording) {
                    viewModel.onStopRecording()
                } else {
                    viewModel.onStartRecording()
                }
            },
            containerColor = if(uiState.isRecording) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp)
        ) {
            Icon(
                imageVector = if(uiState.isRecording) Icons.Filled.Stop else Icons.Filled.Mic,
                contentDescription = if(uiState.isRecording) "Stop recording" else "Start recording"
            )
        }
    }
}

@Composable
private fun VoiceNoteRow(note:VoiceNote) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = { playNote(context, note.filePath) }) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "Play",
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
        Column(modifier = Modifier.padding(start = 8.dp)) {
            Text(
                text = if(note.isFromMe) "You" else note.senderId,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "${note.durationMillis / 1000}s",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}

private fun playNote(context:android.content.Context, filePath:String) {
    if(filePath.isBlank()) {
        android.widget.Toast.makeText(context, "Recording too short to play", android.widget.Toast.LENGTH_SHORT).show()
        return
    }
    val file = java.io.File(filePath)
    if(!file.exists() || file.length() == 0L) {
        android.widget.Toast.makeText(context, "Voice note file is missing or empty", android.widget.Toast.LENGTH_SHORT).show()
        return
    }
    val player = MediaPlayer()
    try {
        player.setDataSource(filePath)
        player.setOnErrorListener { mp, what, extra ->
            android.widget.Toast.makeText(context, "Playback error ($what/$extra)", android.widget.Toast.LENGTH_SHORT).show()
            mp.release()
            true
        }
        player.prepare()
        player.start()
        player.setOnCompletionListener { it.release() }
    } catch(e:Exception) {
        android.widget.Toast.makeText(context, "Couldn't play: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
        player.release()
    }
}