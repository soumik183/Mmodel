package com.m.model.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.m.model.ui.screens.BrowseScreen
import com.m.model.ui.screens.ChatScreen
import com.m.model.ui.screens.DetailScreen
import com.m.model.ui.screens.DownloadsScreen
import com.m.model.ui.screens.HomeScreen

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
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    NavigationBarItem(
                        selected = tab == 0,
                        onClick = { tab = 0 },
                        icon = {
                            Icon(
                                imageVector = Icons.Rounded.Home,
                                contentDescription = null,
                            )
                        },
                        label = { Text("Home") },
                        colors = navItemColors(),
                    )
                    NavigationBarItem(
                        selected = tab == 1,
                        onClick = { tab = 1 },
                        icon = {
                            Icon(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = null,
                            )
                        },
                        label = { Text("Explore") },
                        colors = navItemColors(),
                    )
                    NavigationBarItem(
                        selected = tab == 2,
                        onClick = {
                            tab = 2
                            vm.refreshDevice()
                        },
                        icon = {
                            Icon(
                                imageVector = Icons.Rounded.Download,
                                contentDescription = null,
                            )
                        },
                        label = { Text("Downloads") },
                        colors = navItemColors(),
                    )
                    NavigationBarItem(
                        selected = tab == 3,
                        onClick = { tab = 3 },
                        icon = {
                            Icon(
                                imageVector = Icons.Rounded.ChatBubble,
                                contentDescription = null,
                            )
                        },
                        label = { Text("Chat") },
                        colors = navItemColors(),
                    )
                }
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

@Composable
private fun navItemColors(): androidx.compose.material3.NavigationBarItemColors =
    NavigationBarItemDefaults.colors(
        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
        selectedTextColor = MaterialTheme.colorScheme.onSurface,
        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )
