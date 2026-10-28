package com.m.model.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalModelTest {
    @Test
    fun importedRecordNeverResolvesAnAppFile() {
        val record = DownloadRecord(
            id = "local|content://model",
            modelId = "local/model.gguf",
            modelName = "model",
            quant = "Imported",
            dirPath = "",
            files = listOf(
                DownloadFile(
                    remoteName = "model.gguf",
                    localName = "model.gguf",
                    bytesTotal = 1_000L,
                    bytesDone = 1_000L,
                    done = true,
                ),
            ),
            status = DownloadStatus.DONE,
            sourceUri = "content://model",
        )

        assertTrue(record.isImported)
        assertTrue(record.isComplete)
        assertEquals(null, record.firstPath)
    }

    @Test
    fun everyTaskCategoryKeepsGgufDownloadable() {
        assertTrue(ModelCategory.ALL.filters.isEmpty())
        ModelCategory.entries
            .filter { it != ModelCategory.ALL }
            .forEach { category ->
                assertTrue(category.filters.contains("gguf"))
            }
    }
}
