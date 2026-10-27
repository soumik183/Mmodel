package com.m.model.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.m.model.data.DeviceSpecs
import com.m.model.data.DownloadRecord
import com.m.model.data.DownloadStatus
import com.m.model.data.Fit
import com.m.model.data.GgufVariant
import com.m.model.data.ModelCard
import com.m.model.data.ModelFit
import com.m.model.ui.MainViewModel
import com.m.model.ui.components.FitBadge
import com.m.model.ui.components.InfoRow
import com.m.model.ui.components.LetterAvatar
import com.m.model.ui.components.MessageCard
import com.m.model.ui.components.SectionHeader
import com.m.model.ui.components.fitColors
import com.m.model.ui.components.fitIcon
import com.m.model.ui.components.fitLabel
import com.m.model.ui.formatBytes
import com.m.model.ui.formatContext
import com.m.model.ui.formatCount
import com.m.model.ui.formatParams

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    vm: MainViewModel,
    modelId: String,
    onBack: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenChat: (String) -> Unit,
) {
    LaunchedEffect(modelId) { vm.openModel(modelId) }

    val state = vm.detail
    val card = state.card
    val device = vm.device
    val context = LocalContext.current
    val downloads by vm.downloads.collectAsState()
    var pendingDownload by remember(modelId) { mutableStateOf<GgufVariant?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = card?.name ?: "Model details",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Rounded.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                actions = {
                    if (card != null) {
                        IconButton(onClick = { openOnHuggingFace(context, card.id) }) {
                            Icon(
                                imageVector = Icons.Rounded.OpenInNew,
                                contentDescription = "Open on Hugging Face",
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        when {
            state.error != null && card == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(20.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    MessageCard(
                        icon = Icons.Rounded.CloudOff,
                        title = "Could not load model",
                        message = state.error.orEmpty(),
                        actionLabel = "Retry",
                        onAction = vm::retryDetail,
                    )
                }
            }

            card == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CircularProgressIndicator()
                        Text(
                            text = "Reading model card…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            else -> {
                DetailContent(
                    card = card,
                    device = device,
                    downloads = downloads,
                    downloadNotice = vm.downloadNotice,
                    detailError = state.error,
                    modifier = Modifier.padding(padding),
                    onOpen = { openOnHuggingFace(context, card.id) },
                    onDownload = { variant ->
                        if (needsDownloadWarning(card, variant, device)) {
                            pendingDownload = variant
                        } else {
                            vm.startDownload(card, variant)
                        }
                    },
                    onPause = vm::pauseDownload,
                    onResume = vm::resumeDownload,
                    onClearNotice = vm::clearDownloadNotice,
                    onRetry = vm::retryDetail,
                    onOpenChat = onOpenChat,
                    onOpenDownloads = onOpenDownloads,
                    onRefreshDevice = vm::refreshDevice,
                )
            }
        }
    }

    pendingDownload?.let { variant ->
        card?.let {
            DownloadWarningDialog(
                card = it,
                variant = variant,
                device = device,
                onDismiss = { pendingDownload = null },
                onConfirm = {
                    pendingDownload = null
                    vm.startDownload(it, variant)
                },
            )
        }
    }
}


private val chatPipelines = setOf(
    "text-generation",
    "image-text-to-text",
    "text2text-generation",
    "conversational",
)

private fun ModelCard.chatPipelineOk(): Boolean {
    val pipeline = model.pipelineTag?.lowercase() ?: return true
    return pipeline in chatPipelines
}

private fun needsDownloadWarning(card: ModelCard, variant: GgufVariant, device: DeviceSpecs): Boolean =
    variant.fit.fit != Fit.WORKS ||
        variant.fit.storageBlocked ||
        variant.sharded ||
        variant.bytes <= 0L ||
        !device.canRunLocalChat ||
        card.model.gguf?.chatTemplate.isNullOrBlank() ||
        !card.chatPipelineOk()

@Composable
private fun DownloadWarningDialog(
    card: ModelCard,
    variant: GgufVariant,
    device: DeviceSpecs,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val (accent, _) = fitColors(variant.fit.fit)
    val title = when (variant.fit.fit) {
        Fit.WONT_RUN -> "This model likely won't run"
        Fit.SLOW -> "This model may be slow"
        Fit.UNKNOWN -> "Fit can't be verified"
        Fit.WORKS -> "Download with a warning?"
    }
    val warnings = buildList {
        when (variant.fit.fit) {
            Fit.WONT_RUN -> add("The fit check says this quant is too large for this phone. Local chat may crash or fail after downloading.")
            Fit.SLOW -> add("This quant is close to the safe RAM limit. Generation may be slow or stop when other apps are running.")
            Fit.UNKNOWN -> add("Hugging Face did not publish a complete file size, so fit and storage use cannot be verified.")
            Fit.WORKS -> Unit
        }
        if (variant.bytes <= 0L) {
            add("The exact download size is unknown. Progress starts after Hugging Face responds.")
        }
        if (variant.fit.storageBlocked) {
            add("Free storage is below the 300 MB reserve. Free up space before downloading.")
        }
        if (variant.sharded) {
            add("Local chat currently supports single-file GGUFs only. This sharded download can be saved, but not loaded in Chat.")
        }
        if (!device.canRunLocalChat) {
            add("Local chat needs a 64-bit ARM device. The download can be saved, but chat is unavailable here.")
        }
        if (card.model.gguf?.chatTemplate.isNullOrBlank()) {
            add("This repo does not expose a chat template. Chat will use a fallback template when available.")
        }
        if (!card.chatPipelineOk()) {
            add("Hugging Face lists this repo as ${card.model.pipelineTag}, not text generation. Chat may not work.")
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Rounded.WarningAmber,
                contentDescription = null,
                tint = accent,
            )
        },
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "${card.name} · ${variant.quant}",
                    style = MaterialTheme.typography.titleSmall,
                )
                warnings.forEach { warning ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.bodyMedium,
                            color = accent,
                        )
                        Text(
                            text = warning,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text(
                    text = if (variant.bytes > 0L) {
                        "Download size: ${formatBytes(variant.bytes)}"
                    } else {
                        "Download size: reported by Hugging Face on start"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = if (variant.fit.fit == Fit.WONT_RUN) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                ),
            ) {
                Text("Download anyway")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surface,
    )
}

@Composable
private fun DetailContent(
    card: ModelCard,
    device: DeviceSpecs,
    downloads: List<DownloadRecord>,
    downloadNotice: String?,
    detailError: String?,
    modifier: Modifier = Modifier,
    onOpen: () -> Unit,
    onDownload: (GgufVariant) -> Unit,
    onPause: (String) -> Unit,
    onResume: (String) -> Unit,
    onClearNotice: () -> Unit,
    onRetry: () -> Unit,
    onOpenChat: (String) -> Unit,
    onOpenDownloads: () -> Unit,
    onRefreshDevice: () -> Unit,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                LetterAvatar(name = card.name, size = 68.dp)
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        text = card.name,
                        style = MaterialTheme.typography.headlineSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = card.author.ifBlank { "Hugging Face" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FitBadge(fit = card.fit.fit)
            }
        }

        detailError?.let { error ->
            item {
                MessageCard(
                    icon = Icons.Rounded.CloudOff,
                    title = "Some details may be out of date",
                    message = error,
                    actionLabel = "Retry",
                    onAction = onRetry,
                )
            }
        }

        if (card.model.tags.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    card.model.tags.take(18).forEach { tag ->
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        ) {
                            Text(
                                text = tag,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }

        item { FitCard(card = card, device = device) }
        item { ComparisonCard(card = card, device = device, onRefresh = onRefreshDevice) }

        item {
            SectionHeader(
                title = "Download a quantization",
                subtitle = when {
                    card.variants.isEmpty() -> "No GGUF files in this repo"
                    card.variants.all { it.bytes <= 0L } -> "Sizes not reported by Hugging Face"
                    else -> "Real file sizes from Hugging Face"
                },
                trailing = {
                    if (card.variants.isNotEmpty()) {
                        TextButton(onClick = onOpenDownloads) {
                            Text("Downloads")
                        }
                    }
                },
            )
        }

        downloadNotice?.let { notice ->
            item {
                DownloadNoticeCard(text = notice, onDismiss = onClearNotice)
            }
        }

        if (card.variants.isEmpty()) {
            item {
                MessageCard(
                    icon = Icons.Rounded.Download,
                    title = "No downloadable GGUF found",
                    message = "This repo does not publish GGUF weight files with sizes. Try a model tagged GGUF, or open it on Hugging Face to inspect its files.",
                    actionLabel = "Open Hugging Face",
                    onAction = onOpen,
                )
            }
        } else {
            items(card.variants, key = { it.key }, contentType = { "variant" }) { variant ->
                val record = downloads.firstOrNull {
                    it.id == DownloadRecord.idFor(card.id, variant)
                }
                VariantCard(
                    variant = variant,
                    record = record,
                    device = device,
                    onDownload = { onDownload(variant) },
                    onPause = { record?.let { onPause(it.id) } },
                    onResume = { record?.let { onResume(it.id) } },
                    onOpenChat = onOpenChat,
                    onOpenDownloads = onOpenDownloads,
                )
            }

            item {
                Text(
                    text = "One download runs at a time. If a connection drops, Resume continues from the partial file. Multi-shard GGUFs are saved together but local chat currently loads single-file GGUFs only.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item { ModelDetailsCard(card = card) }
        item { DeviceCheckCard(device = device, onRefresh = onRefreshDevice) }

        item {
            Button(
                onClick = onOpen,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = MaterialTheme.shapes.large,
            ) {
                Icon(
                    imageVector = Icons.Rounded.OpenInNew,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text("Open on Hugging Face")
            }
        }

        item {
            Text(
                text = "Fit uses actual Hugging Face file sizes and this phone's live memory and storage. RAM is estimated at 1.15× the quant size plus 600 MB overhead, compared with a safe budget of min(75% of total RAM, total RAM − 1.5 GB). Context size, runtime and background apps can change the result.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun VariantCard(
    variant: GgufVariant,
    record: DownloadRecord?,
    device: DeviceSpecs,
    onDownload: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onOpenChat: (String) -> Unit,
    onOpenDownloads: () -> Unit,
) {
    val complete = record?.isComplete == true
    val shownFit = if (complete && record != null) {
        ModelFit.ramFit(record.totalBytes, device).fit
    } else {
        variant.fit.fit
    }
    val canChat = complete &&
        record != null &&
        record.files.size == 1 &&
        device.canRunLocalChat &&
        shownFit != Fit.WONT_RUN

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = variant.quant,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        if (variant.sharded) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceVariant,
                            ) {
                                Text(
                                    text = "${variant.fileCount} files",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    Text(
                        text = buildString {
                            append(formatBytes(variant.bytes))
                            append(" · ")
                            append(variant.files.firstOrNull()?.name ?: "GGUF")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                FitBadge(fit = shownFit)
            }

            when {
                complete && record != null -> {
                    if (canChat) {
                        Button(onClick = { onOpenChat(record.id) }) {
                            Icon(
                                imageVector = Icons.Rounded.ChatBubble,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Chat with this quant")
                        }
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = "Downloaded",
                                style = MaterialTheme.typography.labelMedium,
                            )
                            TextButton(onClick = onOpenDownloads) {
                                Text("Open in Downloads")
                            }
                        }
                    }
                }

                record != null && record.status == DownloadStatus.DOWNLOADING -> {
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = if (record.totalBytes > 0L) {
                                "${(progress * 100).toInt()}% · ${formatBytes(record.doneBytes)} of ${formatBytes(record.totalBytes)}"
                            } else {
                                "${formatBytes(record.doneBytes)} downloaded · size pending"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedButton(onClick = onPause) {
                            Icon(
                                imageVector = Icons.Rounded.Pause,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("Pause")
                        }
                    }
                }

                record != null -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(onClick = onResume) {
                            Icon(
                                imageVector = Icons.Rounded.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("Resume")
                        }
                        TextButton(onClick = onOpenDownloads) {
                            Text("Open in Downloads")
                        }
                    }
                }

                else -> {
                    Button(
                        onClick = onDownload,
                        enabled = !variant.fit.storageBlocked,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Download,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (variant.fit.storageBlocked) {
                                "Not enough storage"
                            } else if (variant.bytes > 0L) {
                                "Download ${formatBytes(variant.bytes)}"
                            } else {
                                "Download"
                            },
                        )
                    }
                }
            }

            record?.let { current ->
                if (current.status != DownloadStatus.DONE || current.error != null) {
                    current.error?.takeIf { it.isNotBlank() }?.let { error ->
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
                }
                Text(
                    text = current.dirPath,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (!complete && variant.fit.storageBlocked) {
                Text(
                    text = "This quant does not fit in the free storage reported right now. Free up space and tap Recheck.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DownloadNoticeCard(text: String, onDismiss: () -> Unit) {
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
private fun FitCard(card: ModelCard, device: DeviceSpecs) {
    val (background, content) = fitColors(card.fit.fit)
    val bestVariant = card.bestVariant
    val downloadBytes = card.downloadBytes
    val title = when {
        card.fit.storageBlocked -> "Not enough storage"
        card.fit.fit == Fit.WORKS -> "Works on this phone"
        card.fit.fit == Fit.SLOW -> "Likely runs slowly"
        card.fit.fit == Fit.WONT_RUN -> "Won't run on this phone"
        else -> "Fit unknown"
    }
    val message = when {
        card.fit.storageBlocked ->
            "The best quant needs ${formatBytes(card.downloadBytes)}, but only ${formatBytes(device.freeStorageBytes)} is free."

        card.fit.fit == Fit.WORKS ->
            "The best quant sits inside your safe RAM budget."

        card.fit.fit == Fit.SLOW ->
            "It may load, but expect low speed and memory pressure."

        card.fit.fit == Fit.WONT_RUN ->
            "It needs more RAM than this phone can safely give an app."

        card.variants.isNotEmpty() ->
            "Hugging Face did not publish a file size, so fit can't be verified."

        else ->
            "No weight-file sizes were published for this repo."
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = background,
        contentColor = content,
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    imageVector = fitIcon(card.fit.fit),
                    contentDescription = null,
                    modifier = Modifier.size(30.dp),
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(text = title, style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = content.copy(alpha = 0.8f),
                    )
                }
            }

            card.fit.estimatedRamBytes?.let { needs ->
                val progress = if (device.inferenceBudgetBytes > 0L) {
                    (needs.toFloat() / device.inferenceBudgetBytes / 1.5f).coerceIn(0f, 1f)
                } else {
                    1f
                }
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(CircleShape),
                    color = content,
                    trackColor = content.copy(alpha = 0.18f),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "Est. ${formatBytes(needs)} RAM",
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Text(
                        text = "Budget ${formatBytes(device.inferenceBudgetBytes)}",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }

            if (bestVariant != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "Best quant · ${bestVariant.quant}",
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Text(
                        text = formatBytes(downloadBytes),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }

            if (downloadBytes != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "Free storage now",
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Text(
                        text = formatBytes(device.freeStorageBytes),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                val after = device.freeStorageBytes - downloadBytes
                if (after > 0L) {
                    Text(
                        text = "About ${formatBytes(after)} left after this download.",
                        style = MaterialTheme.typography.labelSmall,
                        color = content.copy(alpha = 0.8f),
                    )
                }
            }

            if (card.fit.storageBlocked) {
                Text(
                    text = "Free up space or choose a smaller quantization.",
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

@Composable
private fun ComparisonCard(card: ModelCard, device: DeviceSpecs, onRefresh: () -> Unit) {
    val bestVariant = card.bestVariant
    val downloadBytes = card.downloadBytes
    val needsRam = card.fit.estimatedRamBytes
    val memoryFit = ModelFit.memoryFit(needsRam, device.inferenceBudgetBytes)
    val storageFits = ModelFit.storageFits(downloadBytes, device.freeStorageBytes)
    val storageFit = when (storageFits) {
        true -> Fit.WORKS
        false -> Fit.WONT_RUN
        null -> Fit.UNKNOWN
    }
    val singleFile = card.variants.any { !it.sharded }
    val runtimeFit = when {
        !device.canRunLocalChat -> Fit.WONT_RUN
        card.variants.isEmpty() -> Fit.WONT_RUN
        singleFile -> Fit.WORKS
        else -> Fit.WONT_RUN
    }
    val freeAfter = downloadBytes?.let { device.freeStorageBytes - it }
    val shortfall = downloadBytes?.let {
        (it + ModelFit.STORAGE_RESERVE_BYTES - device.freeStorageBytes).coerceAtLeast(0L)
    }
    val storageDetail = when (storageFits) {
        true -> freeAfter?.let {
            "${formatBytes(it)} after · ${ModelFit.STORAGE_RESERVE_BYTES / 1_000_000} MB reserve"
        } ?: "Free space checked"
        false -> shortfall?.takeIf { it > 0L }?.let {
            "Short by ${formatBytes(it)} incl. reserve"
        } ?: "Not enough free space"
        null -> "Size not published"
    }
    val modelContext = (card.model.gguf?.contextLength
        ?: card.model.config?.maxPositionEmbeddings)?.takeIf { it > 0L }
    val memoryFormula = if (card.variants.isNotEmpty()) {
        "1.15× GGUF size + 600 MB overhead"
    } else {
        "1.2× weight size + 900 MB overhead"
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text("Model vs your phone", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "The exact values behind the fit verdict",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = onRefresh) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Recheck")
                }
            }

            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "MODEL NEEDS",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(40.dp))
                Text(
                    text = "YOUR PHONE",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ComparisonRow(
                label = "Memory",
                modelPrimary = needsRam?.let { "${formatBytes(it)} RAM" } ?: "Unknown",
                modelSecondary = if (needsRam != null) "Estimated at load" else "No file size published",
                devicePrimary = "${formatBytes(device.inferenceBudgetBytes)} safe",
                deviceSecondary = "of ${formatBytes(device.totalRamBytes)} physical RAM",
                status = memoryFit,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ComparisonRow(
                label = "Download",
                modelPrimary = formatBytes(downloadBytes),
                modelSecondary = bestVariant?.let {
                    if (it.sharded) "${it.quant} · ${it.fileCount} shards" else "${it.quant} · single file"
                } ?: "No GGUF file",
                devicePrimary = "${formatBytes(device.freeStorageBytes)} free",
                deviceSecondary = storageDetail,
                status = storageFit,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ComparisonRow(
                label = "Local chat",
                modelPrimary = when {
                    card.variants.isEmpty() -> "No GGUF file"
                    singleFile -> "Single-file GGUF"
                    else -> "Multi-shard GGUF"
                },
                modelSecondary = when {
                    card.variants.isEmpty() -> "Chat needs GGUF weights"
                    singleFile -> "At least one quant is chat-ready"
                    else -> "Chat cannot load shards"
                },
                devicePrimary = if (device.canRunLocalChat) "arm64 ready" else "${device.abi} only",
                deviceSecondary = "llama.cpp runtime",
                status = runtimeFit,
            )

            if (modelContext != null) {
                Text(
                    text = "Model context: ${formatContext(modelContext)}. Mmodel caps local chat at 2K tokens to protect memory.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = "Memory = $memoryFormula. Safe budget = min(75% of RAM, RAM − 1.5 GB). Download check reserves ${ModelFit.STORAGE_RESERVE_BYTES / 1_000_000} MB for Android.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ComparisonRow(
    label: String,
    modelPrimary: String,
    modelSecondary: String,
    devicePrimary: String,
    deviceSecondary: String,
    status: Fit,
) {
    val (background, content) = fitColors(status)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = modelPrimary,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = modelSecondary,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Surface(
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .size(28.dp),
                shape = CircleShape,
                color = background,
                contentColor = content,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = fitIcon(status),
                        contentDescription = "$label: ${fitLabel(status)}",
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = devicePrimary,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.End,
                )
                Text(
                    text = deviceSecondary,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}

@Composable
private fun ModelDetailsCard(card: ModelCard) {
    val bestVariant = card.bestVariant
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(text = "Model details", style = MaterialTheme.typography.titleMedium)
            InfoRow(label = "Parameters", value = formatParams(card.parameters))
            InfoRow(
                label = if (card.ggufVariants > 0) "Best-fit GGUF" else "Weight files",
                value = if (bestVariant != null) {
                    "${formatBytes(card.downloadBytes)} · ${bestVariant.quant}"
                } else {
                    formatBytes(card.downloadBytes)
                },
            )
            if (card.ggufVariants > 0) {
                InfoRow(label = "GGUF options", value = card.ggufVariants.toString())
            }
            card.model.gguf?.contextLength?.let { contextLength ->
                InfoRow(label = "Context", value = formatContext(contextLength))
            }
            InfoRow(label = "Task", value = pretty(card.model.pipelineTag))
            InfoRow(label = "Library", value = card.model.library ?: "—")
            InfoRow(label = "Downloads", value = formatCount(card.model.downloads))
            InfoRow(label = "Likes", value = formatCount(card.model.likes))
            InfoRow(
                label = "Updated",
                value = card.model.lastModified?.take(10) ?: "—",
            )
            InfoRow(label = "Model ID", value = card.id)
        }
    }
}

@Composable
private fun DeviceCheckCard(device: DeviceSpecs, onRefresh: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Your phone",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onRefresh) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Recheck")
                }
            }
            InfoRow("Fit budget", formatBytes(device.inferenceBudgetBytes))
            InfoRow("Total RAM", formatBytes(device.totalRamBytes))
            InfoRow("Free RAM", formatBytes(device.availableRamBytes))
            InfoRow("Free storage", formatBytes(device.freeStorageBytes))
            InfoRow("Chipset", device.soc)
            InfoRow("CPU", "${device.cpuCores} cores · ${device.abi}")
            InfoRow("Graphics", "GLES ${device.glEsVersion}")
            InfoRow("System", device.androidVersion)
            InfoRow(
                "Local chat",
                if (device.canRunLocalChat) "Supported · llama.cpp" else "Unavailable · needs arm64",
            )
        }
    }
}

private fun pretty(value: String?): String {
    if (value.isNullOrBlank()) return "—"
    return value
        .replace('_', ' ')
        .replace('-', ' ')
        .replaceFirstChar { it.titlecase() }
}

private fun openOnHuggingFace(context: Context, modelId: String) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse("https://huggingface.co/$modelId")),
        )
    }
}
