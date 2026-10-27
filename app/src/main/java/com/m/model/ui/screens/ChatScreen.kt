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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.m.model.data.DownloadRecord
import com.m.model.data.Fit
import com.m.model.data.ModelFit
import com.m.model.ui.ChatMessage
import com.m.model.ui.ChatRole
import com.m.model.ui.ChatState
import com.m.model.ui.MainViewModel
import com.m.model.ui.components.FitBadge
import com.m.model.ui.components.LetterAvatar
import com.m.model.ui.components.MessageCard
import com.m.model.ui.components.SectionHeader
import com.m.model.ui.formatBytes
import com.m.model.ui.formatContext

@Composable
fun ChatScreen(
    vm: MainViewModel,
    modifier: Modifier = Modifier,
    onBrowse: () -> Unit,
) {
    val state by vm.chat.collectAsState()
    val records by vm.downloads.collectAsState()
    val device = vm.device
    val downloaded = records.filter { it.isComplete }
    val selected = records.firstOrNull { it.id == state.recordId }
    var input by rememberSaveable { mutableStateOf("") }
    var modelMenu by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    val lastLength = state.messages.lastOrNull()?.text?.length ?: 0
    LaunchedEffect(state.messages.size, lastLength) {
        if (state.messages.isNotEmpty()) {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            if (lastVisible >= state.messages.lastIndex - 1) {
                listState.scrollToItem(state.messages.lastIndex)
            }
        }
    }

    val submit: () -> Unit = {
        val text = input.trim()
        if (text.isNotEmpty() && state.ready && !state.generating) {
            vm.sendMessage(text)
            input = ""
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding(),
    ) {
        ChatHeader(
            state = state,
            selected = selected,
            downloaded = downloaded,
            menuExpanded = modelMenu,
            onMenuExpanded = { modelMenu = it },
            onSelect = { recordId ->
                modelMenu = false
                vm.selectChatRecord(recordId)
            },
            onBrowse = onBrowse,
            onUnload = vm::unloadChat,
            onClear = vm::clearChat,
        )

        when {
            selected == null -> {
                EmptyChat(
                    downloaded = downloaded,
                    device = device,
                    canRun = device.canRunLocalChat,
                    onSelect = { vm.selectChatRecord(it) },
                    onBrowse = onBrowse,
                    modifier = Modifier.weight(1f),
                )
            }

            !state.ready -> {
                LoadModelPane(
                    record = selected,
                    state = state,
                    device = device,
                    onLoad = vm::loadChatModel,
                    onBrowse = onBrowse,
                    modifier = Modifier.weight(1f),
                )
            }

            else -> {
                Column(modifier = Modifier.weight(1f)) {
                    if (state.messages.isEmpty()) {
                        WelcomePane(
                            state = state,
                            onSuggest = { text ->
                                vm.sendMessage(text)
                            },
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items(state.messages, key = { it.id }) { message ->
                                ChatBubble(message = message)
                            }
                            item { Spacer(Modifier.height(4.dp)) }
                        }
                    }

                    state.error?.let { error ->
                        InlineError(text = error)
                    }
                    state.status?.let { status ->
                        Text(
                            text = status,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    ChatInput(
                        value = input,
                        onValueChange = { if (it.length <= 4_000) input = it },
                        onSend = submit,
                        generating = state.generating,
                        stopping = state.stopping,
                        onStop = vm::stopChat,
                        contextLength = state.contextLength,
                        threads = state.threads,
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatHeader(
    state: ChatState,
    selected: DownloadRecord?,
    downloaded: List<DownloadRecord>,
    menuExpanded: Boolean,
    onMenuExpanded: (Boolean) -> Unit,
    onSelect: (String) -> Unit,
    onBrowse: () -> Unit,
    onUnload: () -> Unit,
    onClear: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Chat", style = MaterialTheme.typography.headlineSmall)
                Text(
                    text = "Private inference on this phone",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Memory,
                        contentDescription = null,
                        modifier = Modifier.size(13.dp),
                    )
                    Text(
                        text = if (state.ready) "Loaded" else "Offline",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        ) {
            Row(
                modifier = Modifier.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                LetterAvatar(name = selected?.modelName ?: "Mmodel", size = 38.dp)
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = selected?.modelName ?: "No model loaded",
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = selected?.let { "${it.quant} · ${formatBytes(it.totalBytes)}" }
                            ?: "Choose a downloaded model",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                if (state.ready) {
                    IconButton(onClick = onClear) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteSweep,
                            contentDescription = "Clear conversation",
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    IconButton(onClick = onUnload) {
                        Icon(
                            imageVector = Icons.Rounded.PowerSettingsNew,
                            contentDescription = "Unload model",
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }

                Box {
                    TextButton(
                        onClick = { onMenuExpanded(true) },
                        enabled = !state.loadingModel && !state.generating,
                    ) {
                        Text(if (selected == null) "Choose" else "Change")
                        Icon(
                            imageVector = Icons.Rounded.KeyboardArrowDown,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { onMenuExpanded(false) },
                    ) {
                        if (downloaded.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text("No downloaded models") },
                                onClick = {
                                    onMenuExpanded(false)
                                    onBrowse()
                                },
                            )
                        } else {
                            downloaded.forEach { record ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(
                                                text = record.modelName,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            Text(
                                                text = "${record.quant} · ${formatBytes(record.totalBytes)}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    },
                                    onClick = { onSelect(record.id) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyChat(
    downloaded: List<DownloadRecord>,
    device: com.m.model.data.DeviceSpecs,
    canRun: Boolean,
    onSelect: (String) -> Unit,
    onBrowse: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            ) {
                Column(
                    modifier = Modifier.padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SmartToy,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = "Chat that stays on your phone",
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(
                        text = "Download a GGUF quantization, then load it here. Prompts, replies and the model itself never leave the device.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (downloaded.isEmpty()) {
                        Button(onClick = onBrowse) {
                            Text("Browse models")
                        }
                    }
                }
            }
        }

        if (!canRun) {
            item {
                MessageCard(
                    icon = Icons.Rounded.WarningAmber,
                    title = "This build needs 64-bit ARM",
                    message = "The bundled llama.cpp engine is compiled for arm64-v8a phones. You can still browse and download models.",
                )
            }
        }

        if (downloaded.isNotEmpty()) {
            item {
                SectionHeader(
                    title = "Downloaded models",
                    subtitle = "Tap one to load it into RAM",
                )
            }
            items(downloaded, key = { it.id }) { record ->
                DownloadedModelRow(
                    record = record,
                    device = device,
                    onClick = { onSelect(record.id) },
                )
            }
        } else {
            item {
                MessageCard(
                    icon = Icons.Rounded.ChatBubble,
                    title = "No downloaded models",
                    message = "Open a model in Explore and tap Download next to a quantization. Small 4-bit quants are the safest place to start.",
                    actionLabel = "Explore models",
                    onAction = onBrowse,
                )
            }
        }

        item {
            Text(
                text = "Local chat is experimental. Very large models can still be killed by Android's memory manager, so start with a quant marked Works.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DownloadedModelRow(
    record: DownloadRecord,
    device: com.m.model.data.DeviceSpecs,
    onClick: () -> Unit,
) {
    val fit = remember(record.totalBytes, device.inferenceBudgetBytes) {
        ModelFit.ramFit(record.totalBytes, device)
    }
    ElevatedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LetterAvatar(name = record.modelName, size = 44.dp)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = record.modelName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${record.quant} · ${formatBytes(record.totalBytes)}" +
                        if (record.files.size > 1) " · ${record.files.size} shards" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FitBadge(fit = fit.fit)
        }
    }
}

@Composable
private fun LoadModelPane(
    record: DownloadRecord,
    state: ChatState,
    device: com.m.model.data.DeviceSpecs,
    onLoad: () -> Unit,
    onBrowse: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val fit = remember(record.totalBytes, device.inferenceBudgetBytes) {
        ModelFit.ramFit(record.totalBytes, device)
    }
    val canLoad = device.canRunLocalChat && record.files.size == 1 && fit.fit != Fit.WONT_RUN
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        state.error?.let { error ->
            InlineError(text = error)
        }

        if (state.loadingModel) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Column(
                    modifier = Modifier.padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(30.dp),
                        strokeWidth = 3.dp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        text = "Loading ${record.modelName}…",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "Reading the GGUF from storage into RAM. Large models can take a minute.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                    )
                }
            }
        } else {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        LetterAvatar(name = record.modelName, size = 52.dp)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = record.modelName,
                                style = MaterialTheme.typography.titleLarge,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "${record.quant} · ${formatBytes(record.totalBytes)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        FitBadge(fit = fit.fit)
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Metric(
                            label = "RAM estimate",
                            value = formatBytes(fit.estimatedRamBytes),
                            modifier = Modifier.weight(1f),
                        )
                        Metric(
                            label = "Safe budget",
                            value = formatBytes(device.inferenceBudgetBytes),
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Metric(
                            label = "Context",
                            value = if (state.contextLength > 0) {
                                formatContext(state.contextLength.toLong())
                            } else {
                                formatContext(record.contextLength)
                            },
                            modifier = Modifier.weight(1f),
                        )
                        Metric(
                            label = "Threads",
                            value = if (state.threads > 0) state.threads.toString() else "Auto",
                            modifier = Modifier.weight(1f),
                        )
                    }

                    val note = when {
                        !device.canRunLocalChat -> "Local chat needs a 64-bit ARM phone."
                        record.files.size > 1 ->
                            "Chat needs a single-file GGUF. This download has ${record.files.size} shards."
                        fit.fit == Fit.WONT_RUN ->
                            "This model needs more RAM than this phone can safely give an app. Choose a smaller quant."
                        fit.fit == Fit.SLOW ->
                            "This is close to the memory limit. Responses may be slow."
                        else -> null
                    }
                    if (note != null) {
                        Text(
                            text = note,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = onLoad,
                            enabled = canLoad,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Load model")
                        }
                        OutlinedButton(onClick = onBrowse) {
                            Text("Find smaller")
                        }
                    }
                }
            }
        }

        Text(
            text = "The model is loaded with a conservative 2K context. Start with a short conversation and unload it when you are done to free RAM.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun WelcomePane(
    state: ChatState,
    onSuggest: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.SmartToy,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "Model ready",
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    text = "Everything below happens on this phone. Ask a question, paste some text, or try a prompt.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Text(
            text = "Try a prompt",
            style = MaterialTheme.typography.titleMedium,
        )
        Suggestion("Explain what a GGUF file is in two sentences.") { onSuggest(it) }
        Suggestion("Give me three ways to save battery on Android.") { onSuggest(it) }
        Suggestion("Write a short, friendly poem about phones.") { onSuggest(it) }

        if (state.status != null) {
            Text(
                text = state.status,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Suggestion(text: String, onClick: (String) -> Unit) {
    Surface(
        onClick = { onClick(text) },
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.ChatBubble,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ChatBubble(message: ChatMessage) {
    val user = message.role == ChatRole.USER
    val shape = if (user) {
        RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp, bottomEnd = 8.dp, bottomStart = 22.dp)
    } else {
        RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp, bottomEnd = 22.dp, bottomStart = 8.dp)
    }
    val color = if (user) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
    }
    val content = if (user) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .align(if (user) Alignment.CenterEnd else Alignment.CenterStart),
            shape = shape,
            color = color,
            contentColor = content,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(
                    text = if (user) "You" else "Mmodel",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = content.copy(alpha = 0.65f),
                )
                SelectionContainer {
                    Text(
                        text = when {
                            message.streaming && message.text.isBlank() -> "Thinking…"
                            message.streaming -> message.text + "▍"
                            message.text.isBlank() -> "Stopped."
                            else -> message.text
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                message.meta?.let { meta ->
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.labelSmall,
                        color = content.copy(alpha = 0.6f),
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatInput(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    generating: Boolean,
    stopping: Boolean,
    onStop: () -> Unit,
    contextLength: Int,
    threads: Int,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Message the model…") },
                    maxLines = 5,
                    shape = RoundedCornerShape(22.dp),
                    enabled = !generating,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { onSend() }),
                )
                if (generating) {
                    FilledIconButton(
                        onClick = onStop,
                        enabled = !stopping,
                    ) {
                        if (stopping) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.StopCircle,
                                contentDescription = "Stop generating",
                            )
                        }
                    }
                } else {
                    FilledIconButton(
                        onClick = onSend,
                        enabled = value.isNotBlank(),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Send,
                            contentDescription = "Send message",
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Local · private",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = buildString {
                        if (contextLength > 0) append(formatContext(contextLength.toLong()))
                        if (threads > 0) {
                            if (isNotEmpty()) append(" · ")
                            append("$threads threads")
                        }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Metric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun InlineError(text: String) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.WarningAmber,
                contentDescription = null,
                modifier = Modifier.size(17.dp),
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
