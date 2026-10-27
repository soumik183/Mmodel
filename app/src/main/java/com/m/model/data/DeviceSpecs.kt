package com.m.model.data

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.StatFs

import androidx.compose.runtime.Immutable

@Immutable
data class DeviceSpecs(
    val brand: String,
    val model: String,
    val androidVersion: String,
    val soc: String,
    val abi: String,
    val cpuCores: Int,
    val glEsVersion: String,
    val totalRamBytes: Long,
    val availableRamBytes: Long,
    val freeStorageBytes: Long,
    val totalStorageBytes: Long,
    val supportedAbis: List<String> = emptyList(),
) {
    val displayName: String
        get() = listOf(brand, model)
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .ifBlank { "Android device" }

    /**
     * RAM we consider safe for a model: 75% of total memory, with 1.5 GB reserved
     * for Android and background apps. Used only for the fit estimate.
     */
    val inferenceBudgetBytes: Long
        get() = minOf(totalRamBytes - 1_500_000_000L, (totalRamBytes * 0.75).toLong())
            .coerceAtLeast(0L)

    /** The bundled llama.cpp runtime is compiled for 64-bit ARM only. */
    val canRunLocalChat: Boolean
        get() = supportedAbis.any { it == "arm64-v8a" }

    companion object {
        fun read(context: Context): DeviceSpecs {
            val activityManager =
                context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memory = ActivityManager.MemoryInfo().also { activityManager.getMemoryInfo(it) }
            // Models live in app-specific external storage; use that volume's free space.
            val storageRoot = context.getExternalFilesDir(null) ?: context.filesDir
            val storage = runCatching { StatFs(storageRoot.absolutePath) }.getOrNull()
                ?: runCatching { StatFs(context.filesDir.absolutePath) }.getOrNull()

            val soc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                listOf(Build.SOC_MANUFACTURER, Build.SOC_MODEL)
                    .filter { it.isNotBlank() && !it.equals(Build.UNKNOWN, ignoreCase = true) }
                    .joinToString(" ")
                    .ifBlank { Build.HARDWARE.replace('_', ' ') }
            } else {
                Build.HARDWARE.replace('_', ' ')
            }

            return DeviceSpecs(
                brand = Build.MANUFACTURER.orEmpty().replaceFirstChar { it.titlecase() },
                model = Build.MODEL.orEmpty(),
                androidVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
                soc = soc,
                abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown",
                cpuCores = Runtime.getRuntime().availableProcessors(),
                glEsVersion = activityManager.deviceConfigurationInfo.glEsVersion
                    .ifBlank { "unknown" },
                totalRamBytes = memory.totalMem,
                availableRamBytes = memory.availMem,
                freeStorageBytes = storage?.availableBytes ?: 0L,
                totalStorageBytes = storage?.totalBytes ?: 0L,
                supportedAbis = Build.SUPPORTED_ABIS.toList(),
            )
        }
    }
}
