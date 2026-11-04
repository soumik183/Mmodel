package com.m.model.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.unit.Dp
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
import com.m.model.ui.components.ShimmerBox
import com.m.model.ui.components.SpecTile
import com.m.model.ui.components.SpinningIconButton
import com.m.model.ui.formatBytes
import com.m.model.ui.formatParams
import com.m.model.ui.theme.BrandGradient

@Composable
fun HomeScreen(
    vm: MainViewModel,
    modifier: Modifier = Modifier,
    onOpenModel: (String) -> Unit,
    onExplore: () -> Unit,
    listState: LazyListState = rememberLazyListState(),
) {
    val device = vm.device
    val recommendations = vm.recommendations
    val recLoading = vm.recLoading
    val recError = vm.recError

    LazyColumn(
        state = listState,
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
                    SpinningIconButton(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = "Refresh recommendations",
                        spinning = recLoading,
                        onClick = vm::refreshRecommendations,
                    )
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
                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                        // 82% of the viewport reads as a deliberate peek of the
                        // next card, and clamps keep it sane on tablets.
                        val cardWidth = (maxWidth * 0.82f).coerceIn(268.dp, 396.dp)
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(
                                items = recommendations,
                                key = { it.id },
                                contentType = { "recommendation" },
                            ) { card ->
                                RecommendationCard(
                                    card = card,
                                    width = cardWidth,
                                    onClick = { onOpenModel(card.id) },
                                )
                            }
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
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BrandMark(size = 48.dp)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Mmodel",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = "Local AI • On-Device",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.primary,
            border = BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
            ),
            tonalElevation = 1.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = "Hugging Face",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                )
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
                        .background(
                            if (device.canRunLocalChat) Color(0xFF7DEBDF) else Color(0xFFF3C56B),
                        ),
                )
                Text(
                    text = if (device.canRunLocalChat) {
                        "CHAT READY ON THIS PHONE"
                    } else {
                        "BROWSE ONLY · LOCAL CHAT NEEDS ARM64"
                    },
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecommendationCard(card: ModelCard, width: Dp, onClick: () -> Unit) {
    // Everything inside scales from the card width so the same composable works
    // as a narrow phone card or a wide tablet card.
    val compact = width < 300.dp
    val padding = if (compact) 14.dp else 16.dp
    AnimatedVisibility(
        visible = true,
        enter = fadeIn(animationSpec = tween(350)) +
            slideInVertically(animationSpec = tween(350)) { it / 5 },
    ) {
        Surface(
            onClick = onClick,
            modifier = Modifier
                .width(width)
                .heightIn(min = if (compact) 178.dp else 192.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        ),
        tonalElevation = 2.dp,
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
                modifier = Modifier.padding(padding),
                verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 12.dp),
                ) {
                    LetterAvatar(name = card.name, size = if (compact) 40.dp else 46.dp)
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
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
                    }
                    FitBadge(fit = card.fit.fit)
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
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
                    card.bestVariant?.let { best ->
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ) {
                            Text(
                                text = best.quant,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                            )
                        }
                    }
                }

                val ram = card.fit.estimatedRamBytes
                Text(
                    text = ram?.let { "~${formatBytes(it)} RAM to run" } ?: "RAM estimate unavailable",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
    }
}

/**
 * Responsive skeleton twin of [RecommendationCard]: the same horizontal rhythm
 * (gradient strip, title block, badge, chips, RAM line) so the swap into real
 * cards does not jolt the layout.
 */
@Composable
private fun RecommendationLoading() {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val count = when {
            maxWidth > 900.dp -> 4
            maxWidth > 600.dp -> 3
            else -> 2
        }
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            userScrollEnabled = false,
        ) {
            items(count) {
                RecommendationCardSkeleton(
                    width = (maxWidth * 0.82f).coerceIn(268.dp, 396.dp),
                )
            }
        }
    }
}

/** A single shimmering card shaped like a recommendation card. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecommendationCardSkeleton(width: Dp) {
    val padding = 16.dp
    Surface(
        modifier = Modifier.width(width).heightIn(min = 192.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        ),
        tonalElevation = 2.dp,
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer),
            )
            Column(
                modifier = Modifier.padding(padding),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ShimmerBox(
                        modifier = Modifier.size(46.dp),
                        shape = RoundedCornerShape(16.dp),
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ShimmerBox(modifier = Modifier.fillMaxWidth(0.7f).height(16.dp))
                        ShimmerBox(modifier = Modifier.fillMaxWidth(0.42f).height(12.dp))
                    }
                    ShimmerBox(
                        modifier = Modifier.width(84.dp).height(24.dp),
                        shape = CircleShape,
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    ShimmerBox(
                        modifier = Modifier.width(96.dp).height(20.dp),
                        shape = CircleShape,
                    )
                    ShimmerBox(
                        modifier = Modifier.width(64.dp).height(20.dp),
                        shape = CircleShape,
                    )
                    ShimmerBox(
                        modifier = Modifier.width(52.dp).height(20.dp),
                        shape = CircleShape,
                    )
                }
                ShimmerBox(modifier = Modifier.fillMaxWidth(0.5f).height(12.dp))
            }
        }
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

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Memory usage",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "${(ramUsed * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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
            }

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
