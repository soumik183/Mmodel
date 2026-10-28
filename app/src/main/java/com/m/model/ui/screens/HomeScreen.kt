package com.m.model.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import com.m.model.ui.theme.BrandGradient

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
            recLoading && recommendations.isEmpty() -> item { RecommendationLoading() }

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
                    title = "No fitting chat model found",
                    message = "No sized, chat-ready GGUF in the current picks fits this phone. Browse the full catalog to choose a smaller quantization.",
                    actionLabel = "Explore models",
                    onAction = onExplore,
                )
            }

            else -> item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (recLoading) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(2.dp)
                                .clip(CircleShape),
                        )
                    }
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(
                            items = recommendations,
                            key = { it.id },
                            contentType = { "recommendation" },
                        ) { card ->
                            RecommendationCard(
                                card = card,
                                onClick = { onOpenModel(card.id) },
                            )
                        }
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
        BrandMark(size = 46.dp)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = "Mmodel", style = MaterialTheme.typography.headlineSmall)
            Text(
                text = "LOCAL AI · ANDROID",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.6.sp,
            )
        }
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            contentColor = MaterialTheme.colorScheme.primary,
            border = BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
            ),
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
            .background(BrandGradient)
            .drawBehind {
                drawCircle(
                    color = Color.White.copy(alpha = 0.065f),
                    radius = size.width * 0.62f,
                    center = Offset(size.width * 0.94f, size.height * 0.04f),
                )
                drawCircle(
                    color = Color.White.copy(alpha = 0.035f),
                    radius = size.width * 0.45f,
                    center = Offset(size.width * 0.06f, size.height * 0.95f),
                )
            },
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF7DEBDF)),
                )
                Text(
                    text = "DEVICE READY",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.82f),
                    letterSpacing = 1.8.sp,
                )
            }
            Text(
                text = "Local AI that fits\nyour phone.",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
            )
            Text(
                text = device.displayName,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.84f),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HeroPill(text = "${formatBytes(device.totalRamBytes)} RAM")
                HeroPill(text = "${formatBytes(device.freeStorageBytes)} free")
            }
            Button(
                onClick = onExplore,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color(0xFF17123B),
                ),
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
private fun HeroPill(text: String) {
    Surface(
        shape = CircleShape,
        color = Color.White.copy(alpha = 0.12f),
        contentColor = Color.White,
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
    Surface(
        onClick = onClick,
        modifier = Modifier
            .width(286.dp)
            .heightIn(min = 196.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
        tonalElevation = 0.dp,
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.secondary,
                            ),
                        ),
                    ),
            )
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LetterAvatar(name = card.name, size = 46.dp)
                    Spacer(Modifier.weight(1f))
                    FitBadge(fit = card.fit.fit)
                }
                Text(
                    text = card.name,
                    style = MaterialTheme.typography.titleMedium,
                    minLines = 2,
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
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Memory,
                                contentDescription = null,
                                modifier = Modifier.size(13.dp),
                            )
                            Text(
                                text = "~${formatBytes(it)} RAM",
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }
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
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Surface(
                    modifier = Modifier.size(38.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Smartphone,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
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
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
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
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Surface(
                    modifier = Modifier.size(32.dp),
                    shape = RoundedCornerShape(11.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(17.dp),
                        )
                    }
                }
                Text(text = "How fit is estimated", style = MaterialTheme.typography.titleMedium)
            }
            Text(
                text = "Mmodel reads the actual GGUF sizes published by a repo, sums multi-shard quants, and compares the smallest option with a safe RAM budget of ${formatBytes(device.inferenceBudgetBytes)}. Downloaded models can then run locally through llama.cpp.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "A guide, not a benchmark.",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
