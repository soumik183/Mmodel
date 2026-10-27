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
        val sharded = card.variants.first { it.sharded }
        assertEquals("Q4_K_M", sharded.quant)
        assertEquals(2, sharded.files.size)
        assertEquals(1_000_000_000L, sharded.bytes)
    }

    @Test
    fun quantLabelsAreReadable() {
        assertEquals("Q4_K_M", ModelFit.quantLabel("Meta-Llama-3.1-8B-Instruct-Q4_K_M.gguf"))
        assertEquals("Q8_0", ModelFit.quantLabel("model.Q8_0.gguf"))
        assertEquals("IQ4_XS", ModelFit.quantLabel("model-IQ4_XS.gguf"))
        assertEquals("BF16", ModelFit.quantLabel("model-BF16.gguf"))
        assertEquals("GGUF", ModelFit.quantLabel("model.gguf"))
    }

    @Test
    fun downloadedModelUsesRamFitWithoutStorageLimit() {
        val tinyStorage = device.copy(freeStorageBytes = 1_000_000L)
        val fit = ModelFit.ramFit(500_000_000L, tinyStorage)

        assertEquals(Fit.WORKS, fit.fit)
        assertTrue(fit.estimatedRamBytes!! > 500_000_000L)
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
    fun auxiliaryGgufFilesAreIgnored() {
        val card = ModelFit.analyze(
            model(
                "model-Q4_K_M.gguf" to 2_000_000_000L,
                "imatrix_unsloth.gguf" to 13_000_000L,
                "MTP/mtp-model-Q4_0.gguf" to 800_000_000L,
                "tokenizer.gguf" to 1_000_000L,
            ),
            device,
        )

        assertEquals(2_000_000_000L, card.downloadBytes)
        assertEquals(1, card.ggufVariants)
    }

    @Test
    fun memoryAndStorageVerdictsAreIndependent() {
        assertEquals(Fit.WORKS, ModelFit.memoryFit(1_000_000_000L, 2_000_000_000L))
        assertEquals(Fit.SLOW, ModelFit.memoryFit(1_500_000_000L, 2_000_000_000L))
        assertEquals(Fit.WONT_RUN, ModelFit.memoryFit(2_100_000_000L, 2_000_000_000L))
        assertEquals(Fit.UNKNOWN, ModelFit.memoryFit(null, 2_000_000_000L))

        assertEquals(true, ModelFit.storageFits(1_000_000_000L, 1_300_000_000L))
        assertEquals(false, ModelFit.storageFits(1_000_000_000L, 1_299_999_999L))
        assertEquals(null, ModelFit.storageFits(null, 1_300_000_000L))
    }

    @Test
    fun unknownWhenNoWeightsArePublished() {
        val card = ModelFit.analyze(HfModel(id = "org/no-weights"), device)

        assertEquals(Fit.UNKNOWN, card.fit.fit)
    }

    @Test
    fun lfsSizeIsUsedWhenTopLevelSizeIsMissing() {
        val card = ModelFit.analyze(
            HfModel(
                id = "org/model",
                siblings = listOf(
                    HfSibling(
                        rfilename = "model-Q4_K_M.gguf",
                        lfs = HfLfs(size = 3_000_000_000L),
                    ),
                ),
            ),
            device,
        )

        assertEquals(3_000_000_000L, card.downloadBytes)
        assertEquals(Fit.SLOW, card.fit.fit)
    }

    @Test
    fun unsizedGgufStillOffersAFileToDownload() {
        val card = ModelFit.analyze(
            HfModel(
                id = "org/model",
                siblings = listOf(HfSibling(rfilename = "model-Q4_K_M.gguf")),
            ),
            device,
        )

        assertEquals(1, card.ggufVariants)
        assertEquals(1, card.variants.size)
        assertEquals(0L, card.variants.single().bytes)
        assertEquals(Fit.UNKNOWN, card.fit.fit)
    }

    @Test
    fun partialShardSizesAreTreatedAsUnknown() {
        val card = ModelFit.analyze(
            HfModel(
                id = "org/model",
                siblings = listOf(
                    HfSibling(rfilename = "model-Q4_K_M-00001-of-00002.gguf", size = 800_000_000L),
                    HfSibling(rfilename = "model-Q4_K_M-00002-of-00002.gguf"),
                ),
            ),
            device,
        )

        assertEquals(0L, card.variants.single().bytes)
        assertEquals(Fit.UNKNOWN, card.fit.fit)
    }

    @Test
    fun unsizedVariantDoesNotReplaceTheBestSizedVariant() {
        val card = ModelFit.analyze(
            HfModel(
                id = "org/model",
                siblings = listOf(
                    HfSibling(rfilename = "model-Q2_K.gguf", size = 800_000_000L),
                    HfSibling(rfilename = "model-Q4_K_M.gguf"),
                ),
            ),
            device,
        )

        assertEquals(800_000_000L, card.downloadBytes)
        assertEquals("Q2_K", card.bestVariant?.quant)
        assertEquals(0L, card.variants.last().bytes)
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
