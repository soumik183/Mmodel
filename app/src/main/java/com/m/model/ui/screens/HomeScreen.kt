package com.m.model.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.DeveloperBoard
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.m.model.data.DeviceSpecs
import com.m.model.data.ModelCard
import com.m.model.ui.MainViewModel
import com.m.model.ui.components.BrandMark
import com.m.model.ui.components.FitBadge
import com.m.model.ui.components.InfoRow
import com.m.model.ui.components.LetterAvatar
import com.m.model.ui.components.MessageCard
import com.m.model.ui.components.MetaItem
import com.m.model.ui.components.SectionHeader
import com.m.model.ui.components.SpecTile
import com.m.model.ui.formatBytes
import com.m.model.ui.formatParams

@Composable
fun HomeScreen(
    vm: MainViewModel,
    modifier: Modifier = Modifier,
    onOpenModel: (String) -> Unit,
    onExplore: () -> Unit,
) {
    val device = vm.device
    val recommendations = vm.recommendations
    val recLoading = vm.recLoading
    val recError = vm.recError

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item { BrandHeader() }
        item { HeroCard(device = device, onExplore = onExplore) }

        item {
            SectionHeader(
                title = "Recommended for you",
                subtitle = "Popular GGUF models that fit your phone",
                trailing = {
                    IconButton(
                        onClick = vm::refreshRecommendations,
                        enabled = !recLoading,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "Refresh recommendations",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        }

        when {
            recLoading -> item { RecommendationLoading() }

            recError != null -> item {
                MessageCard(
                    icon = Icons.Rounded.CloudOff,
                    title = "Could not load picks",
                    message = recError.orEmpty(),
                    actionLabel = "Retry",
                    onAction = vm::refreshRecommendations,
                )
            }

            recommendations.isEmpty() -> item {
                MessageCard(
                    icon = Icons.Rounded.AutoAwesome,
                    title = "No fitting models yet",
                    message = "Your phone's RAM budget did not match the current GGUF list. Try refreshing in a moment.",
                    actionLabel = "Refresh",
                    onAction = vm::refreshRecommendations,
                )
            }

            else -> item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(recommendations, key = { it.id }) { card ->
                        RecommendationCard(
                            card = card,
                            onClick = { onOpenModel(card.id) },
                        )
                    }
                }
            }
        }

        item {
            SectionHeader(
                title = "Your device",
                subtitle = "Live specifications from this phone",
                trailing = {
                    IconButton(onClick = vm::refreshDevice) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "Recheck device",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        }
        item { DeviceCard(device) }
        item { HowItWorksCard(device) }
    }
}

@Composable
private fun BrandHeader() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BrandMark()
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = "Mmodel", style = MaterialTheme.typography.headlineSmall)
            Text(
                text = "MOBILE MODEL",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.4.sp,
            )
        }
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(13.dp),
                )
                Text(text = "Hugging Face", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun HeroCard(device: DeviceSpecs, onExplore: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraLarge)
            .background(
                Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.secondaryContainer,
                    ),
                ),
            ),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "DEVICE READY",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.65f),
                letterSpacing = 1.6.sp,
            )
            Text(
                text = "Local AI that fits your phone.",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                text = device.displayName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoPill(text = "${formatBytes(device.totalRamBytes)} RAM")
                InfoPill(text = "${formatBytes(device.freeStorageBytes)} free")
            }
            Button(
                onClick = onExplore,
                shape = CircleShape,
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
            ) {
                Text(text = "Explore models")
                Spacer(Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.Rounded.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun InfoPill(text: String) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
        )
    }
}

@Composable
private fun RecommendationCard(card: ModelCard, onClick: () -> Unit) {
    ElevatedCard(
        onClick = onClick,
        modifier = Modifier
            .width(272.dp)
            .heightIn(min = 192.dp),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LetterAvatar(name = card.name, size = 44.dp)
                Spacer(Modifier.weight(1f))
                FitBadge(fit = card.fit.fit)
            }
            Text(
                text = card.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = card.author.ifBlank { "Hugging Face" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                MetaItem(
                    icon = Icons.Rounded.Download,
                    text = formatBytes(card.downloadBytes),
                )
                if (card.parameters != null) {
                    MetaItem(
                        icon = Icons.Rounded.Memory,
                        text = formatParams(card.parameters),
                    )
                }
            }
            card.fit.estimatedRamBytes?.let {
                Text(
                    text = "Needs about ${formatBytes(it)} RAM",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun RecommendationLoading() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun DeviceCard(device: DeviceSpecs) {
    val ramUsed = if (device.totalRamBytes > 0L) {
        ((device.totalRamBytes - device.availableRamBytes).toFloat() / device.totalRamBytes)
            .coerceIn(0f, 1f)
    } else {
        0f
    }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Smartphone,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Column {
                    Text(
                        text = "${formatBytes(device.totalRamBytes)} RAM",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "${formatBytes(device.availableRamBytes)} available now",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            LinearProgressIndicator(
                progress = { ramUsed },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(CircleShape),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SpecTile(
                    icon = Icons.Rounded.Memory,
                    label = "Total RAM",
                    value = formatBytes(device.totalRamBytes),
                    modifier = Modifier.weight(1f),
                )
                SpecTile(
                    icon = Icons.Rounded.Storage,
                    label = "Free storage",
                    value = formatBytes(device.freeStorageBytes),
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SpecTile(
                    icon = Icons.Rounded.DeveloperBoard,
                    label = "CPU cores",
                    value = "${device.cpuCores}",
                    modifier = Modifier.weight(1f),
                )
                SpecTile(
                    icon = Icons.Rounded.Speed,
                    label = "Graphics",
                    value = "GLES ${device.glEsVersion}",
                    modifier = Modifier.weight(1f),
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            InfoRow(label = "Chipset", value = device.soc)
            InfoRow(label = "System", value = device.androidVersion)
            InfoRow(label = "ABI", value = device.abi)
            InfoRow(
                label = "Local chat",
                value = if (device.canRunLocalChat) "Supported · llama.cpp" else "Unavailable · needs arm64",
            )
        }
    }
}

@Composable
private fun HowItWorksCard(device: DeviceSpecs) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Text(text = "How fit is estimated", style = MaterialTheme.typography.titleMedium)
            }
            Text(
                text = "Mmodel reads the actual GGUF sizes published by a repo, sums multi-shard quants, and compares the smallest option with a safe RAM budget of ${formatBytes(device.inferenceBudgetBytes)}. Downloaded models can then run locally through llama.cpp.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
            )
            Text(
                text = "A guide, not a benchmark.",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
