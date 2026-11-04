package com.m.model.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.m.model.ui.components.MessageCard
import com.m.model.ui.components.ShimmerBox
import com.m.model.ui.components.SpinningIconButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.Locale

private const val MAX_ENTRIES = 500

/**
 * Full-storage directory picker used to point new downloads at any folder.
 * Lists directories only, navigates like a file manager and hands the chosen
 * absolute path back to the caller. Read access relies on All Files Access,
 * which the caller is expected to gate before showing this screen.
 */
@Composable
fun FolderPickerScreen(
    initialPath: String,
    onBack: () -> Unit,
    /** Returns null on success, otherwise a readable reason shown inline. */
    onUse: (String) -> String?,
) {
    val root = remember {
        if (File("/storage").isDirectory) {
            "/storage"
        } else {
            android.os.Environment.getExternalStorageDirectory().absolutePath
        }
    }
    var currentPath by remember {
        mutableStateOf(File(initialPath).takeIf { it.isDirectory }?.absolutePath ?: root)
    }
    var entries by remember(currentPath) { mutableStateOf<List<File>?>(null) }
    var capped by remember(currentPath) { mutableStateOf(false) }
    var error by remember(currentPath) { mutableStateOf<String?>(null) }
    var reload by remember { mutableStateOf(0) }
    var applyError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(currentPath, reload) {
        entries = null
        error = null
        applyError = null
        val result = withContext(Dispatchers.IO) { runCatching { listDirectories(currentPath) } }
        result
            .onSuccess { (found, wasCapped) ->
                entries = found
                capped = wasCapped
            }
            .onFailure {
                error = it.message ?: "Could not read this folder."
            }
    }

    fun navigateTo(path: String) {
        currentPath = path
    }

    fun goUp() {
        val parent = File(currentPath).parentFile?.absolutePath
        if (parent == null || currentPath == root) {
            onBack()
        } else {
            navigateTo(parent)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            IconButton(onClick = ::goUp) {
                Icon(
                    imageVector = Icons.Rounded.ArrowBack,
                    contentDescription = "Back",
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Choose download folder",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = currentPath,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            SpinningIconButton(
                imageVector = Icons.Rounded.Refresh,
                contentDescription = "Reload folder",
                spinning = entries == null && error == null,
                onClick = { reload++ },
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
                        icon = Icons.Rounded.Storage,
                        title = "Could not read this folder",
                        message = error.orEmpty(),
                        actionLabel = "Retry",
                        onAction = { reload++ },
                    )
                }
            }

            entries == null -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 20.dp,
                        end = 20.dp,
                        top = 8.dp,
                        bottom = 24.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    userScrollEnabled = false,
                ) {
                    items(6) { PickerRowSkeleton() }
                }
            }

            else -> {
                val list = entries!!
                when {
                    list.isEmpty() -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 20.dp),
                            contentAlignment = Alignment.TopCenter,
                        ) {
                            Text(
                                text = "No subfolders here. You can still use this folder.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 32.dp),
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
                            items(list, key = { it.absolutePath }) { dir ->
                                PickerRow(dir = dir, onClick = { navigateTo(dir.absolutePath) })
                            }
                            if (capped) {
                                item {
                                    Text(
                                        text = "Showing the first $MAX_ENTRIES folders.",
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
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            applyError?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Button(
                onClick = {
                    applyError = onUse(currentPath)
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.Rounded.FolderOpen,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = "  Use this folder",
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
private fun PickerRow(dir: File, onClick: () -> Unit) {
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
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.Folder,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = dir.name.ifBlank { dir.absolutePath },
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = dir.absolutePath,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun PickerRowSkeleton() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
        ),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ShimmerBox(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape),
                shape = CircleShape,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                ShimmerBox(modifier = Modifier.fillMaxWidth(0.5f).height(14.dp))
                ShimmerBox(modifier = Modifier.fillMaxWidth(0.35f).height(10.dp))
            }
        }
    }
}

private fun listDirectories(path: String): Pair<List<File>, Boolean> {
    val dir = File(path)
    if (!dir.exists()) {
        throw IOException("The folder no longer exists: ${dir.absolutePath}")
    }
    if (!dir.isDirectory) {
        throw IOException("Not a folder: ${dir.absolutePath}")
    }
    val children = dir.listFiles { file -> file.isDirectory }
        ?: throw IOException("Could not read this folder. Grant All Files Access or pick another one.")
    val sorted = children
        .sortedBy { it.name.lowercase(Locale.ROOT) }
        .take(MAX_ENTRIES)
    return sorted to (children.size > MAX_ENTRIES)
}
