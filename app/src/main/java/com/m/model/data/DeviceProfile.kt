package com.m.model.data

enum class PerformanceProfile { SPEED, BATTERY_RAM, ADAPTIVE }

data class ResolvedProfile(
    val profile: PerformanceProfile,
    val threads: Int,
    val contextLength: Int,
    val batchSize: Int,
    val useMmap: Boolean,
    val label: String,
)

object DeviceProfile {
    fun resolve(device: DeviceSpecs, requested: PerformanceProfile, modelContext: Long? = null): ResolvedProfile {
        val effective = when (requested) {
            PerformanceProfile.ADAPTIVE ->
                if (device.totalRamBytes < 6_000_000_000L) PerformanceProfile.BATTERY_RAM
                else PerformanceProfile.SPEED
            else -> requested
        }
        val threads = threadsFor(device, effective)
        val context = contextFor(device, effective, modelContext)
        val batch = if (effective == PerformanceProfile.SPEED) 512 else 256
        val label = "${threads} threads · ${context / 1024}K ctx"
        return ResolvedProfile(effective, threads, context, batch, true, label)
    }

    fun threadsFor(device: DeviceSpecs, profile: PerformanceProfile): Int {
        val cores = device.cpuCores.coerceAtLeast(1)
        return when (profile) {
            PerformanceProfile.SPEED -> (cores - 1).coerceIn(2, cores)
            PerformanceProfile.BATTERY_RAM -> (cores / 2).coerceIn(1, 4)
            PerformanceProfile.ADAPTIVE -> (cores - 2).coerceIn(2, 6)
        }
    }

    fun contextFor(device: DeviceSpecs, profile: PerformanceProfile, modelContext: Long?): Int {
        val cap = when (profile) {
            PerformanceProfile.SPEED -> 4096
            PerformanceProfile.BATTERY_RAM -> 2048
            PerformanceProfile.ADAPTIVE -> if (device.totalRamBytes >= 8_000_000_000L) 4096 else 2048
        }
        val wanted = (modelContext ?: cap.toLong()).coerceIn(512L, cap.toLong())
        return wanted.toInt()
    }
}

fun resolveProfile(device: DeviceSpecs, requested: PerformanceProfile, modelContext: Long? = null) =
    DeviceProfile.resolve(device, requested, modelContext)

fun threadsFor(device: DeviceSpecs, profile: PerformanceProfile) =
    DeviceProfile.threadsFor(device, profile)

fun contextFor(device: DeviceSpecs, profile: PerformanceProfile, modelContext: Long?) =
    DeviceProfile.contextFor(device, profile, modelContext)
