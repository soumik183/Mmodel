package com.m.model.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.m.model.data.DeviceSpecs
import com.m.model.data.DownloadRecord
import com.m.model.data.DownloadStatus
import com.m.model.data.Fit
import com.m.model.data.ModelFit
import com.m.model.ui.MainViewModel
import com.m.model.ui.components.FitBadge
import com.m.model.ui.components.LetterAvatar
import com.m.model.ui.components.MessageCard
import com.m.model.ui.components.SectionHeader
import com.m.model.ui.formatBytes
import com.m.model.ui.formatContext

@Composable
fun DownloadsScreen(
    vm: MainViewModel,
    modifier: Modifier = Modifier,
    onOpenModel: (String) -> Unit,
    onChat: (String) -> Unit,
    onBrowse: () -> Unit,
) {
    val records by vm.downloads.collectAsState()
    val device = vm.device
    val context = LocalContext.current
    var deleteTarget by remember { mutableStateOf<DownloadRecord?>(null) }

    val freeBytes = vm.downloadFreeBytes()
    val readyBytes = records.filter { it.isComplete }.sumOf { it.totalBytes }
    val active = records.firstOrNull { it.status == DownloadStatus.DOWNLOADING }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            SectionHeader(
                title = "Downloads",
                subtitle = if (records.isEmpty()) {
                    "Models saved on this phone"
                } else {
                    "${records.size} model${if (records.size == 1) "" else "s"} · ${formatBytes(readyBytes)} ready"
                },
                trailing = {
                    IconButton(onClick = vm::refreshDevice) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "Recheck storage",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        }

        item {
            StorageCard(
                path = vm.downloadsRootPath,
                freeBytes = freeBytes,
                readyBytes = readyBytes,
                onCopy = { copyPath(context, vm.downloadsRootPath) },
            )
        }

        vm.downloadNotice?.let { notice ->
            item {
                NoticeCard(text = notice, onDismiss = vm::clearDownloadNotice)
            }
        }

        if (active != null) {
            item {
                Text(
                    text = "Downloading ${active.modelName} (${active.quant})…",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        if (records.isEmpty()) {
            item {
                MessageCard(
                    icon = Icons.Rounded.Download,
                    title = "No models on this phone yet",
                    message = "Pick a GGUF quantization on a model page, then track its progress and path here. Downloads stay in app-private storage.",
                    actionLabel = "Explore models",
                    onAction = onBrowse,
                )
            }
        } else {
            items(records, key = { it.id }) { record ->
                DownloadCard(
                    record = record,
                    device = device,
                    onChat = { onChat(record.id) },
                    onPause = { vm.pauseDownload(record.id) },
                    onResume = { vm.resumeDownload(record.id) },
                    onDelete = { deleteTarget = record },
                    onOpenModel = { onOpenModel(record.modelId) },
                    onCopyPath = { copyPath(context, record.dirPath) },
                )
            }
        }

        item {
            Text(
                text = "Files are stored in the app's external files directory. They are removed when you uninstall Mmodel. Gated Hugging Face repos need the licence accepted on the website before they can be downloaded.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete downloaded model?") },
            text = {
                Text(
                    "This removes ${target.displayName} (${formatBytes(target.totalBytes)}) from this phone. " +
                        "The Hugging Face copy is not affected.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.deleteDownload(target.id)
                        deleteTarget = null
                    },
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun StorageCard(
    path: String,
    freeBytes: Long,
    readyBytes: Long,
    onCopy: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Folder,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                )
                Text(
                    text = "Model folder",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onCopy) {
                    Icon(
                        imageVector = Icons.Rounded.ContentCopy,
                        contentDescription = "Copy folder path",
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Text(
                text = path,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.12f))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        text = formatBytes(freeBytes),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "free storage",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = formatBytes(readyBytes),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "models on device",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                    )
                }
            }
        }
    }
}

@Composable
private fun NoticeCard(text: String, onDismiss: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = text,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
            )
            TextButton(onClick = onDismiss) {
                Text("Dismiss")
            }
        }
    }
}

@Composable
private fun DownloadCard(
    record: DownloadRecord,
    device: DeviceSpecs,
    onChat: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onDelete: () -> Unit,
    onOpenModel: () -> Unit,
    onCopyPath: () -> Unit,
) {
    val fit = remember(record.totalBytes, device.inferenceBudgetBytes) {
        ModelFit.ramFit(record.totalBytes, device)
    }
    val canChat = record.isComplete &&
        record.files.size == 1 &&
        device.canRunLocalChat &&
        fit.fit != Fit.WONT_RUN

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                LetterAvatar(name = record.modelName, size = 48.dp)
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        text = record.modelName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = record.quant,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                StatusPill(record.status)
            }

            Text(
                text = record.dirPath,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (record.isComplete) {
                    FitBadge(fit = fit.fit)
                }
                Text(
                    text = if (record.isComplete) {
                        formatBytes(record.totalBytes)
                    } else {
                        "${formatBytes(record.doneBytes)} / ${formatBytes(record.totalBytes)}"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                if (record.files.size > 1) {
                    Text(
                        text = "${record.files.size} shards",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                record.contextLength?.let {
                    Text(
                        text = formatContext(it),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (!record.isComplete) {
                val progress = if (record.totalBytes > 0L) {
                    (record.doneBytes.toFloat() / record.totalBytes).coerceIn(0f, 1f)
                } else {
                    0f
                }
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(CircleShape),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
                Text(
                    text = "${(progress * 100).toInt()}% · ${statusLabel(record.status)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            record.error?.takeIf { it.isNotBlank() }?.let { error ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ErrorOutline,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.error,
                    )
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            if (record.isComplete && !canChat) {
                val reason = when {
                    record.files.size > 1 -> "Chat needs a single-file GGUF. This download is split."
                    !device.canRunLocalChat -> "Chat needs a 64-bit ARM device."
                    fit.fit == Fit.WONT_RUN -> "Not enough safe RAM to load this model."
                    else -> "This model cannot be loaded for chat."
                }
                Text(
                    text = reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                when (record.status) {
                    DownloadStatus.DONE -> {
                        Button(onClick = onChat, enabled = canChat) {
                            Icon(
                                imageVector = Icons.Rounded.ChatBubble,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Chat")
                        }
                    }

                    DownloadStatus.DOWNLOADING -> {
                        OutlinedButton(onClick = onPause) {
                            Icon(
                                imageVector = Icons.Rounded.Pause,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Pause")
                        }
                    }

                    DownloadStatus.PAUSED, DownloadStatus.ERROR -> {
                        Button(onClick = onResume) {
                            Icon(
                                imageVector = Icons.Rounded.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Resume")
                        }
                    }
                }

                TextButton(onClick = onOpenModel) {
                    Text("Model page")
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onCopyPath) {
                    Icon(
                        imageVector = Icons.Rounded.ContentCopy,
                        contentDescription = "Copy model folder path",
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = "Delete download",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusPill(status: DownloadStatus) {
    val icon = when (status) {
        DownloadStatus.DONE -> Icons.Rounded.CheckCircle
        DownloadStatus.DOWNLOADING -> Icons.Rounded.Download
        DownloadStatus.PAUSED -> Icons.Rounded.Pause
        DownloadStatus.ERROR -> Icons.Rounded.ErrorOutline
    }
    val color = when (status) {
        DownloadStatus.DONE -> MaterialTheme.colorScheme.primary
        DownloadStatus.DOWNLOADING -> MaterialTheme.colorScheme.primary
        DownloadStatus.PAUSED -> MaterialTheme.colorScheme.onSurfaceVariant
        DownloadStatus.ERROR -> MaterialTheme.colorScheme.error
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = color,
        )
        Text(
            text = statusLabel(status),
            style = MaterialTheme.typography.labelSmall,
            color = color,
        )
    }
}

private fun statusLabel(status: DownloadStatus): String = when (status) {
    DownloadStatus.DONE -> "Ready"
    DownloadStatus.DOWNLOADING -> "Downloading"
    DownloadStatus.PAUSED -> "Paused"
    DownloadStatus.ERROR -> "Needs attention"
}

private fun copyPath(context: Context, path: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    clipboard?.setPrimaryClip(ClipData.newPlainText("Model path", path))
    Toast.makeText(context, "Path copied", Toast.LENGTH_SHORT).show()
}
