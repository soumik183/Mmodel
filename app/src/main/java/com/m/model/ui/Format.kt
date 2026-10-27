package com.m.model.ui

import java.util.Locale

fun formatBytes(bytes: Long?): String {
    if (bytes == null || bytes <= 0L) return "—"
    val gb = bytes / 1_073_741_824.0
    val mb = bytes / 1_048_576.0
    return when {
        gb >= 1.0 -> String.format(Locale.US, "%.1f GB", gb)
        mb >= 1.0 -> String.format(Locale.US, "%.0f MB", mb)
        else -> String.format(Locale.US, "%.0f KB", bytes / 1024.0)
    }
}

fun formatCount(value: Long?): String {
    if (value == null || value <= 0L) return "—"
    return when {
        value >= 1_000_000_000L -> String.format(Locale.US, "%.1fB", value / 1_000_000_000.0)
        value >= 1_000_000L -> String.format(Locale.US, "%.1fM", value / 1_000_000.0)
        value >= 1_000L -> String.format(Locale.US, "%.1fK", value / 1_000.0)
        else -> value.toString()
    }
}

fun formatParams(value: Long?): String {
    if (value == null || value <= 0L) return "—"
    return when {
        value >= 1_000_000_000_000L -> String.format(Locale.US, "%.1fT", value / 1_000_000_000_000.0)
        value >= 1_000_000_000L -> String.format(Locale.US, "%.1fB", value / 1_000_000_000.0)
        value >= 1_000_000L -> String.format(Locale.US, "%.1fM", value / 1_000_000.0)
        else -> String.format(Locale.US, "%.0fK", value / 1_000.0)
    }
}

fun formatContext(tokens: Long?): String {
    if (tokens == null || tokens <= 0L) return "—"
    return String.format(Locale.US, "%.0fK tokens", tokens / 1024.0)
}
