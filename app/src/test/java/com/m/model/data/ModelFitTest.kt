package com.m.model.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelFitTest {
    private val device = DeviceSpecs(
        brand = "Test",
        model = "Phone",
        androidVersion = "Android 15",
        soc = "Test SoC",
        abi = "arm64-v8a",
        cpuCores = 8,
        glEsVersion = "3.2",
        totalRamBytes = 8_000_000_000L,
        availableRamBytes = 4_000_000_000L,
        freeStorageBytes = 64_000_000_000L,
        totalStorageBytes = 128_000_000_000L,
    )

    @Test
    fun smallGgufWorks() {
        val card = ModelFit.analyze(model("model-q4.gguf" to 500_000_000L), device)

        assertEquals(Fit.WORKS, card.fit.fit)
        assertEquals(1, card.ggufVariants)
        assertTrue(card.fit.estimatedRamBytes!! > 500_000_000L)
    }

    @Test
    fun mediumGgufIsSlow() {
        val card = ModelFit.analyze(model("model-q4.gguf" to 3_000_000_000L), device)

        assertEquals(Fit.SLOW, card.fit.fit)
    }

    @Test
    fun hugeGgufWontRun() {
        val card = ModelFit.analyze(model("model-q4.gguf" to 6_000_000_000L), device)

        assertEquals(Fit.WONT_RUN, card.fit.fit)
    }

    @Test
    fun notEnoughStorageWontRun() {
        val tinyStorage = device.copy(freeStorageBytes = 1_000_000_000L)
        val card = ModelFit.analyze(model("model-q4.gguf" to 900_000_000L), tinyStorage)

        assertTrue(card.fit.storageBlocked)
        assertEquals(Fit.WONT_RUN, card.fit.fit)
    }

    @Test
    fun shardsAreSummedAndSmallestQuantIsUsed() {
        val card = ModelFit.analyze(
            model(
                "model-Q4_K_M-00001-of-00002.gguf" to 500_000_000L,
                "model-Q4_K_M-00002-of-00002.gguf" to 500_000_000L,
                "model-Q2_K.gguf" to 700_000_000L,
            ),
            device,
        )

        assertEquals(700_000_000L, card.downloadBytes)
        assertEquals(2, card.ggufVariants)
    }

    @Test
    fun projectorFilesAreIgnored() {
        val card = ModelFit.analyze(
            model(
                "model-Q4_K_M.gguf" to 2_000_000_000L,
                "mmproj-model-f16.gguf" to 500_000_000L,
            ),
            device,
        )

        assertEquals(2_000_000_000L, card.downloadBytes)
        assertEquals(1, card.ggufVariants)
    }

    @Test
    fun unknownWhenNoWeightsArePublished() {
        val card = ModelFit.analyze(HfModel(id = "org/no-weights"), device)

        assertEquals(Fit.UNKNOWN, card.fit.fit)
    }

    private fun model(vararg files: Pair<String, Long>): HfModel = HfModel(
        id = "org/model",
        author = "org",
        downloads = 10_000,
        likes = 100,
        siblings = files.map { (name, size) -> HfSibling(rfilename = name, size = size) },
        gguf = HfGguf(total = 1_000_000_000L, contextLength = 32_768L),
    )
}
