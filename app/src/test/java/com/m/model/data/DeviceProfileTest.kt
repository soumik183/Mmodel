package com.m.model.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceProfileTest {
    private fun device(ramGb: Long, cores: Int, freeRamGb: Long = ramGb / 2) = DeviceSpecs(
        brand = "Test", model = "Phone", androidVersion = "Android 15", soc = "Test",
        abi = "arm64-v8a", cpuCores = cores, glEsVersion = "3.2",
        totalRamBytes = ramGb * 1_000_000_000L, availableRamBytes = freeRamGb * 1_000_000_000L,
        freeStorageBytes = 64_000_000_000L, totalStorageBytes = 128_000_000_000L,
    )

    @Test fun speedUsesMoreThreads() {
        val r = resolveProfile(device(8, 8), PerformanceProfile.SPEED, 8192)
        assertTrue(r.threads >= 6)
        assertEquals(4096, r.contextLength)
    }

    @Test fun batteryUsesFewerThreadsAndSmallerContext() {
        val r = resolveProfile(device(4, 8), PerformanceProfile.BATTERY_RAM, 8192)
        assertTrue(r.threads <= 4)
        assertTrue(r.contextLength <= 2048)
    }

    @Test fun adaptiveRespectsLowRam() {
        val r = resolveProfile(device(3, 6), PerformanceProfile.ADAPTIVE, 8192)
        assertTrue(r.contextLength <= 2048)
    }

    @Test fun contextNeverExceedsModelContext() {
        val r = resolveProfile(device(16, 8), PerformanceProfile.SPEED, 1024)
        assertEquals(1024, r.contextLength)
    }
}
