package com.hanifm.eternaljourney.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hanifm.eternaljourney.audio.AudioFileManager
import com.hanifm.eternaljourney.ui.viewmodel.AudioFilesViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioFilesScreen(
    viewModel: AudioFilesViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val audioFileManager = AudioFileManager(context)
    val bundledFiles by viewModel.bundledAudioFiles.collectAsState()
    val userFiles by viewModel.userAudioFiles.collectAsState()
    val defaultAudioFile by viewModel.defaultAudioFile.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val fileName = uri.lastPathSegment ?: "audio_file"
            viewModel.importAudioFile(
                uri = it,
                originalFileName = fileName,
                onSuccess = { },
                onError = { }
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Audio Files") }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { launcher.launch("audio/*") }
            ) {
                Icon(Icons.Default.Add, contentDescription = "Import Audio")
            }
        }
    ) { paddingValues ->
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                item {
                    Text(
                        text = "Bundled Audio",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(16.dp)
                    )
                }
                if (bundledFiles.isEmpty()) {
                    item {
                        Text(
                            text = "No bundled audio files",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                } else {
                    items(bundledFiles) { file ->
                        AudioFileItem(
                            file = file,
                            isDefault = file.fileName == defaultAudioFile,
                            onSetDefault = { viewModel.setDefaultAudioFile(file.fileName) },
                            onDelete = null, // Can't delete bundled files
                            onPlay = {
                                scope.launch {
                                    val playableUri = audioFileManager.getPlayableUri(file)
                                    playableUri?.let { uri ->
                                        val intent = Intent(Intent.ACTION_VIEW).apply {
                                            setDataAndType(uri, "audio/*")
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        val chooser = Intent.createChooser(intent, "Play audio with")
                                        context.startActivity(chooser)
                                    }
                                }
                            }
                        )
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "My Audio",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(16.dp)
                    )
                }
                if (userFiles.isEmpty()) {
                    item {
                        Text(
                            text = "No imported audio files. Tap + to import.",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                } else {
                    items(userFiles) { file ->
                        AudioFileItem(
                            file = file,
                            isDefault = file.fileName == defaultAudioFile,
                            onSetDefault = { viewModel.setDefaultAudioFile(file.fileName) },
                            onDelete = {
                                viewModel.deleteAudioFile(
                                    fileName = file.fileName,
                                    onSuccess = { },
                                    onError = { }
                                )
                            },
                            onPlay = {
                                scope.launch {
                                    val playableUri = audioFileManager.getPlayableUri(file)
                                    playableUri?.let { uri ->
                                        val intent = Intent(Intent.ACTION_VIEW).apply {
                                            setDataAndType(uri, "audio/*")
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        val chooser = Intent.createChooser(intent, "Play audio with")
                                        context.startActivity(chooser)
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AudioFileItem(
    file: com.hanifm.eternaljourney.audio.AudioFileInfo,
    isDefault: Boolean,
    onSetDefault: () -> Unit,
    onDelete: (() -> Unit)?,
    onPlay: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = "Audio file",
                modifier = Modifier.size(40.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(enabled = onPlay != null) { onPlay?.invoke() }
            ) {
                Text(
                    text = file.displayName,
                    style = MaterialTheme.typography.bodyLarge
                )
                if (isDefault) {
                    Text(
                        text = "Default audio file",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            if (!isDefault) {
                TextButton(onClick = onSetDefault) {
                    Text("Set Default")
                }
            }
            if (onDelete != null && !file.isBundled) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete")
                }
            }
        }
    }
}

