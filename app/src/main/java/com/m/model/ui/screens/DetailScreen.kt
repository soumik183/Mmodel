package com.m.model.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.m.model.data.DeviceSpecs
import com.m.model.data.Fit
import com.m.model.data.ModelCard
import com.m.model.ui.MainViewModel
import com.m.model.ui.components.FitBadge
import com.m.model.ui.components.InfoRow
import com.m.model.ui.components.LetterAvatar
import com.m.model.ui.components.MessageCard
import com.m.model.ui.components.fitColors
import com.m.model.ui.components.fitIcon
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
) {
    LaunchedEffect(modelId) { vm.openModel(modelId) }

    val state = vm.detail
    val card = state.card
    val context = LocalContext.current

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
                    device = vm.device,
                    modifier = Modifier.padding(padding),
                    onOpen = { openOnHuggingFace(context, card.id) },
                )
            }
        }
    }
}

@Composable
private fun DetailContent(
    card: ModelCard,
    device: DeviceSpecs,
    modifier: Modifier = Modifier,
    onOpen: () -> Unit,
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
        item { ModelDetailsCard(card = card) }
        item { DeviceCheckCard(device = device) }

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
                text = "Fit compares the smallest published GGUF file with a RAM budget of 75% of total memory, minus 1.5 GB for Android. Quantization, context size, background apps and your runtime can change real results.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FitCard(card: ModelCard, device: DeviceSpecs) {
    val (background, content) = fitColors(card.fit.fit)
    val title = when {
        card.fit.storageBlocked -> "Not enough storage"
        card.fit.fit == Fit.WORKS -> "Works on this phone"
        card.fit.fit == Fit.SLOW -> "Likely runs slowly"
        card.fit.fit == Fit.WONT_RUN -> "Won't run on this phone"
        else -> "Fit unknown"
    }
    val message = when {
        card.fit.storageBlocked ->
            "The download needs ${formatBytes(card.downloadBytes)}, but only ${formatBytes(device.freeStorageBytes)} is free."

        card.fit.fit == Fit.WORKS ->
            "The smallest quant sits inside your safe RAM budget."

        card.fit.fit == Fit.SLOW ->
            "It may load, but expect low speed and memory pressure."

        card.fit.fit == Fit.WONT_RUN ->
            "It needs more RAM than this phone can safely give an app."

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
private fun ModelDetailsCard(card: ModelCard) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(text = "Model details", style = MaterialTheme.typography.titleMedium)
            InfoRow(label = "Parameters", value = formatParams(card.parameters))
            InfoRow(
                label = if (card.ggufVariants > 0) "Smallest GGUF" else "Weight files",
                value = formatBytes(card.downloadBytes),
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
private fun DeviceCheckCard(device: DeviceSpecs) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(text = "Your phone", style = MaterialTheme.typography.titleMedium)
            InfoRow("Fit budget", formatBytes(device.inferenceBudgetBytes))
            InfoRow("Total RAM", formatBytes(device.totalRamBytes))
            InfoRow("Free RAM", formatBytes(device.availableRamBytes))
            InfoRow("Free storage", formatBytes(device.freeStorageBytes))
            InfoRow("Chipset", device.soc)
            InfoRow("CPU", "${device.cpuCores} cores · ${device.abi}")
            InfoRow("Graphics", "GLES ${device.glEsVersion}")
            InfoRow("System", device.androidVersion)
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
