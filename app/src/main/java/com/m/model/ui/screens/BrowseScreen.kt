package com.m.model.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.m.model.data.ModelCategory
import com.m.model.data.ModelSort
import com.m.model.ui.MainViewModel
import com.m.model.ui.components.MessageCard
import com.m.model.ui.components.ModelRow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.drop

@Composable
fun BrowseScreen(
    vm: MainViewModel,
    modifier: Modifier = Modifier,
    onOpenModel: (String) -> Unit,
    listState: LazyListState = rememberLazyListState(),
) {
    val state = vm.browse
    var sortMenuOpen by remember { mutableStateOf(false) }

    // A new query, category or sort starts at the top. drop(1) ignores the value
    // re-emitted when this screen returns from a model detail, so the previous
    // scroll position survives navigation.
    LaunchedEffect(listState) {
        snapshotFlow { Triple(vm.browse.query, vm.browse.category, vm.browse.sort) }
            .drop(1)
            .collect { runCatching { listState.scrollToItem(0) } }
    }

    LaunchedEffect(listState) {
        snapshotFlow {
            val layoutInfo = listState.layoutInfo
            val last = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            val visibleCount = layoutInfo.visibleItemsInfo.size
            Triple(last, vm.browse.items.size, visibleCount)
        }.collect { (last, count, visibleCount) ->
            if (count > 0) {
                val threshold = (visibleCount.coerceAtLeast(4) + 2).coerceAtMost(10)
                if (last >= count - threshold) {
                    vm.loadMoreBrowse()
                }
            }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Explore",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = when {
                            state.loading && state.items.isEmpty() -> "Reading the Hugging Face catalog…"
                            state.items.isEmpty() -> "Search and discover GGUF models"
                            else -> buildString {
                                append(state.items.size)
                                if (!state.endReached) append("+")
                                append(" models • ${state.category.label}")
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    border = BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                    ),
                    tonalElevation = 1.dp,
                ) {
                    IconButton(
                        onClick = vm::refreshBrowse,
                        modifier = Modifier.size(44.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "Reload models",
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }

            OutlinedTextField(
                value = state.query,
                onValueChange = vm::onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        text = "Search models, authors, tasks…",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { vm.onQueryChange("") }) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Clear search",
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(20.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { vm.searchNow() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    cursorColor = MaterialTheme.colorScheme.primary,
                ),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ModelCategory.entries.forEach { category ->
                        FilterChip(
                            selected = state.category == category,
                            onClick = { vm.setCategory(category) },
                            label = { Text(category.label) },
                            shape = CircleShape,
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                        )
                    }
                }
                Box {
                    Surface(
                        onClick = { sortMenuOpen = true },
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        border = BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                        ),
                    ) {
                        Row(
                            modifier = Modifier
                                .height(36.dp)
                                .padding(start = 12.dp, end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Tune,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(text = state.sort.label, style = MaterialTheme.typography.labelMedium)
                            Icon(
                                imageVector = Icons.Rounded.ArrowDropDown,
                                contentDescription = null,
                            )
                        }
                    }
                    DropdownMenu(
                        expanded = sortMenuOpen,
                        onDismissRequest = { sortMenuOpen = false },
                    ) {
                        ModelSort.entries.forEach { sort ->
                            DropdownMenuItem(
                                text = { Text(sort.label) },
                                onClick = {
                                    sortMenuOpen = false
                                    vm.onSortChange(sort)
                                },
                                trailingIcon = {
                                    if (sort == state.sort) {
                                        Icon(
                                            imageVector = Icons.Rounded.Check,
                                            contentDescription = null,
                                        )
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }

        if (state.loading && state.items.isNotEmpty()) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
                    .height(2.dp),
            )
        }

        when {
            state.loading && state.items.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            state.error != null && state.items.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(20.dp),
                ) {
                    MessageCard(
                        icon = Icons.Rounded.CloudOff,
                        title = "Could not load models",
                        message = state.error.orEmpty(),
                        actionLabel = "Retry",
                        onAction = vm::refreshBrowse,
                    )
                }
            }

            state.items.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(20.dp),
                ) {
                    val hasFilters = state.query.isNotBlank() || state.category != com.m.model.data.ModelCategory.GGUF
                    MessageCard(
                        icon = Icons.Rounded.Search,
                        title = "No models found",
                        message = if (hasFilters) {
                            "Try clearing your search or filters to see more results."
                        } else {
                            "Try a different name, or pick another category."
                        },
                        actionLabel = if (hasFilters) "Clear filters" else null,
                        onAction = if (hasFilters) {
                            {
                                vm.onQueryChange("")
                                vm.setCategory(com.m.model.data.ModelCategory.GGUF)
                            }
                        } else {
                            null
                        },
                    )
                }
            }

            else -> {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(
                        start = 20.dp,
                        end = 20.dp,
                        top = 14.dp,
                        bottom = 24.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (state.error != null) {
                        item(key = "notice", contentType = "notice") {
                            MessageCard(
                                icon = Icons.Rounded.CloudOff,
                                title = "Could not refresh models",
                                message = state.error.orEmpty(),
                                actionLabel = "Retry",
                                onAction = vm::refreshBrowse,
                            )
                        }
                    }
                    items(
                        items = state.items,
                        key = { it.id },
                        contentType = { "model" },
                    ) { card ->
                        ModelRow(card = card, onClick = { onOpenModel(card.id) })
                    }
                    item(key = "footer", contentType = "footer") {
                        state.loadMoreError?.let { error ->
                            MessageCard(
                                icon = Icons.Rounded.CloudOff,
                                title = "Could not load more models",
                                message = error,
                                actionLabel = "Retry",
                                onAction = vm::loadMoreBrowse,
                            )
                        }
                        AnimatedVisibility(
                            visible = state.loadingMore,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically(),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 14.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = "Loading the next 20 models…",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        AnimatedVisibility(
                            visible = state.endReached && !state.loadingMore,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically(),
                        ) {
                            Text(
                                text = "End of the catalog · ${state.items.size} models",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp),
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
