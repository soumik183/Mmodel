package com.m.model.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.m.model.ui.screens.BrowseScreen
import com.m.model.ui.screens.ChatScreen
import com.m.model.ui.screens.DetailScreen
import com.m.model.ui.screens.DownloadsScreen
import com.m.model.ui.screens.HomeScreen

private data class AppTab(val icon: ImageVector, val label: String)

private val appTabs = listOf(
    AppTab(Icons.Rounded.Home, "Home"),
    AppTab(Icons.Rounded.Search, "Explore"),
    AppTab(Icons.Rounded.Download, "Downloads"),
    AppTab(Icons.Rounded.ChatBubble, "Chat"),
)

@Composable
fun MmodelApp(vm: MainViewModel = viewModel()) {
    var tab by rememberSaveable { mutableStateOf(0) }
    var detailId by rememberSaveable { mutableStateOf<String?>(null) }

    val closeDetail: () -> Unit = {
        detailId = null
        vm.closeDetail()
    }

    BackHandler(enabled = detailId != null, onBack = closeDetail)

    val currentDetail = detailId
    if (currentDetail != null) {
        DetailScreen(
            vm = vm,
            modelId = currentDetail,
            onBack = closeDetail,
            onOpenDownloads = {
                detailId = null
                tab = 2
                vm.refreshDevice()
            },
            onOpenChat = { recordId ->
                detailId = null
                vm.openChatFor(recordId)
                tab = 3
            },
        )
    } else {
        LaunchedEffect(tab) {
            if (tab == 1) vm.ensureBrowseLoaded()
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.surfaceContainerLow,
                        ),
                    ),
                ),
        ) {
            Scaffold(
                containerColor = Color.Transparent,
                bottomBar = {
                    MmodelBottomBar(
                        selected = tab,
                        onSelect = { index ->
                            if (index == 2) vm.refreshDevice()
                            tab = index
                        },
                    )
                },
            ) { padding ->
                when (tab) {
                    0 -> HomeScreen(
                        vm = vm,
                        modifier = Modifier.padding(padding),
                        onOpenModel = { detailId = it },
                        onExplore = { tab = 1 },
                    )

                    1 -> BrowseScreen(
                        vm = vm,
                        modifier = Modifier.padding(padding),
                        onOpenModel = { detailId = it },
                    )

                    2 -> DownloadsScreen(
                        vm = vm,
                        modifier = Modifier.padding(padding),
                        onOpenModel = { detailId = it },
                        onChat = { recordId ->
                            vm.openChatFor(recordId)
                            tab = 3
                        },
                        onBrowse = { tab = 1 },
                    )

                    else -> ChatScreen(
                        vm = vm,
                        modifier = Modifier.padding(padding),
                        onBrowse = { tab = 1 },
                    )
                }
            }
        }
    }
}

@Composable
private fun MmodelBottomBar(selected: Int, onSelect: (Int) -> Unit) {
    val dark = isSystemInDarkTheme()
    val shape = RoundedCornerShape(30.dp)
    val glass = Brush.horizontalGradient(
        listOf(
            MaterialTheme.colorScheme.surface.copy(alpha = if (dark) 0.72f else 0.82f),
            MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = if (dark) 0.62f else 0.72f),
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = if (dark) 0.28f else 0.36f),
        ),
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        shape = shape,
        color = Color.Transparent,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (dark) 0.42f else 0.55f),
        ),
        shadowElevation = 14.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(66.dp)
                .clip(shape)
                .background(glass)
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            appTabs.forEachIndexed { index, item ->
                val active = selected == index
                val background by animateColorAsState(
                    targetValue = if (active) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        Color.Transparent
                    },
                    label = "navBackground",
                )
                val content by animateColorAsState(
                    targetValue = if (active) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    label = "navContent",
                )
                val iconScale by animateFloatAsState(
                    targetValue = if (active) 1f else 0.9f,
                    label = "navIconScale",
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(24.dp))
                        .selectable(
                            selected = active,
                            onClick = { onSelect(index) },
                            role = Role.Tab,
                        )
                        .background(background),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = null,
                            modifier = Modifier
                                .size(20.dp)
                                .graphicsLayer {
                                    scaleX = iconScale
                                    scaleY = iconScale
                                },
                            tint = content,
                        )
                        Text(
                            text = item.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = content,
                        )
                    }
                }
            }
        }
    }
}
