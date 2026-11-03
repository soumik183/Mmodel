package com.m.model.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.m.model.data.ModelFiles
import com.m.model.data.ModelFormat
import com.m.model.ui.components.InfoRow
import com.m.model.ui.components.MessageCard
import com.m.model.ui.formatBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.util.Locale

private data class FolderEntry(
    val name: String,
    val relativePath: String,
    val absolutePath: String,
    val sizeBytes: Long,
)

private const val MAX_ENTRIES = 500

/** Read-only view of everything in the current download folder. */
@Composable
fun FolderScreen(rootPath: String, onBack: () -> Unit) {
    val root = remember(rootPath) { File(rootPath) }
    var entries by remember { mutableStateOf<List<FolderEntry>?>(null) }
    var capped by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableStateOf(0) }
    var selected by remember { mutableStateOf<FolderEntry?>(null) }

    LaunchedEffect(rootPath, reload) {
        entries = null
        error = null
        val result = withContext(Dispatchers.IO) { runCatching { walkFolder(root) } }
        result
            .onSuccess { (found, wasCapped) ->
                entries = found
                capped = wasCapped
            }
            .onFailure {
                error = it.message ?: "Could not read this folder."
            }
    }

    val totalBytes = entries?.sumOf { it.sizeBytes } ?: 0L

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Rounded.ArrowBack,
                    contentDescription = "Back to Downloads",
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Model folder",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = rootPath,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.MiddleEllipsis,
                )
            }
            IconButton(onClick = { reload++ }) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = "Reload folder",
                )
            }
        }

        entries?.let { list ->
            Text(
                text = buildString {
                    append("${list.size} file")
                    if (list.size != 1) append("s")
                    append(" · ${formatBytes(totalBytes)}")
                    val gguf = list.count { it.name.endsWith(".gguf", ignoreCase = true) }
                    if (gguf > 0) append(" · $gguf GGUF")
                },
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        when {
            error != null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    MessageCard(
                        icon = Icons.Rounded.CloudOff,
                        title = "Could not read this folder",
                        message = error.orEmpty(),
                        actionLabel = "Retry",
                        onAction = { reload++ },
                    )
                }
            }

            entries == null -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            entries!!.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    MessageCard(
                        icon = Icons.Rounded.FolderOpen,
                        title = "This folder is empty",
                        message = "Downloaded models will appear here. Point the app at a folder that already has GGUFs to see them too.",
                    )
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 20.dp,
                        end = 20.dp,
                        top = 8.dp,
                        bottom = 24.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(entries!!, key = { it.absolutePath }) { entry ->
                        FileRow(entry = entry, onClick = { selected = entry })
                    }
                    if (capped) {
                        item {
                            Text(
                                text = "Showing the first $MAX_ENTRIES files. Narrow the folder to see the rest.",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }
    }

    selected?.let { entry ->
        InspectDialog(entry = entry, onDismiss = { selected = null })
    }
}

@Composable
private fun FileRow(entry: FolderEntry, onClick: () -> Unit) {
    val format = ModelFiles.formatOf(entry.name)
    val formatTag = format.label.take(4).uppercase(Locale.ROOT)

    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
        ),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                modifier = Modifier.size(46.dp),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = formatTag,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = entry.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.MiddleEllipsis,
                )
                Text(
                    text = entry.relativePath.substringBeforeLast('/', "")
                        .ifBlank { "/" },
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.MiddleEllipsis,
                )
            }
            Text(
                text = formatBytes(entry.sizeBytes),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun InspectDialog(entry: FolderEntry, onDismiss: () -> Unit) {
    val format = ModelFiles.formatOf(entry.name)
    val kind = ModelFiles.kindOf(entry.name, format)
    val isGguf = format == ModelFormat.GGUF
    val ggufVersion = remember(entry.absolutePath) { readGgufVersion(File(entry.absolutePath)) }
    val verdict = ModelFiles.chatBlocker(format, kind, fileCount = 1)
        ?: "Ready to chat in Mmodel."

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Rounded.Storage,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        title = { Text("What is this file?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoRow(label = "File", value = entry.name)
                InfoRow(label = "Path", value = entry.absolutePath)
                InfoRow(label = "Size", value = formatBytes(entry.sizeBytes))
                InfoRow(label = "Type", value = format.label)
                InfoRow(label = "Kind", value = kind.label)
                if (isGguf) {
                    InfoRow(
                        label = "GGUF version",
                        value = when {
                            ggufVersion == null -> "not a valid GGUF"
                            ggufVersion < 2 -> "too old (v$ggufVersion)"
                            else -> "v$ggufVersion"
                        },
                    )
                }
                InfoRow(label = "Chat", value = verdict)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
    )
}

private fun walkFolder(root: File): Pair<List<FolderEntry>, Boolean> {
    if (!root.exists()) {
        throw IOException("The folder no longer exists: ${root.absolutePath}")
    }
    if (!root.isDirectory) {
        throw IOException("Not a folder: ${root.absolutePath}")
    }
    val base = root.absolutePath.trimEnd(File.separatorChar) + File.separator
    val out = ArrayList<FolderEntry>(MAX_ENTRIES)
    var capped = false
    try {
        root.walkTopDown().forEach { file ->
            if (file.isFile) {
                if (out.size >= MAX_ENTRIES) {
                    capped = true
                    return@forEach
                }
                val abs = file.absolutePath
                val rel = if (abs.startsWith(base)) abs.removePrefix(base) else abs
                out += FolderEntry(file.name, rel, abs, file.length())
            }
        }
    } catch (_: Exception) {
        // Keep whatever was collected; a readable partial list beats a crash.
    }
    out.sortBy { it.relativePath.lowercase(Locale.ROOT) }
    return out to capped
}

private fun readGgufVersion(file: File): Int? = runCatching {
    FileInputStream(file).use { input ->
        val header = ByteArray(8)
        var read = 0
        while (read < header.size) {
            val n = input.read(header, read, header.size - read)
            if (n <= 0) return null
            read += n
        }
        if (String(header, 0, 4, Charsets.US_ASCII) != "GGUF") return null
        (header[4].toInt() and 0xFF) or
            ((header[5].toInt() and 0xFF) shl 8) or
            ((header[6].toInt() and 0xFF) shl 16) or
            ((header[7].toInt() and 0xFF) shl 24)
    }
}.getOrNull()
