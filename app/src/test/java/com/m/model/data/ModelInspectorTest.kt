package com.m.model.data

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ModelInspectorTest {
    @Test
    fun compatibilityMatrixCoversAllKinds() {
        assertEquals(CompatibilityVerdict.CAN_RUN_TEXT_CHAT, inspectFromName("model-q4_k_m.gguf", 1).verdict)
        assertEquals(CompatibilityVerdict.REFERENCE_ONLY, inspectFromName("model.onnx", 1).verdict)
        assertEquals(CompatibilityVerdict.UNSUPPORTED_HERE, inspectFromName("voice-tts.gguf", 1).verdict)
        assertEquals(CompatibilityVerdict.UNSUPPORTED_HERE, inspectFromName("model-00001-of-00002.gguf", 2).verdict)
    }

    @Test
    fun ggufHeaderIsParsed() {
        val bytes = ByteArray(8)
        bytes[0] = 'G'.code.toByte(); bytes[1] = 'G'.code.toByte()
        bytes[2] = 'U'.code.toByte(); bytes[3] = 'F'.code.toByte()
        bytes[4] = 3; bytes[5] = 0; bytes[6] = 0; bytes[7] = 0
        val header = parseGgufHeader(bytes)
        assertEquals(3, header?.version)
    }

    private fun sampleGguf(): ByteArray {
        val arch = "llama".toByteArray(Charsets.UTF_8)
        val ctxKey = "llama.context_length".toByteArray(Charsets.UTF_8)
        val archKey = "general.architecture".toByteArray(Charsets.UTF_8)
        val buf = ByteBuffer.allocate(4096).order(ByteOrder.LITTLE_ENDIAN)
        buf.put("GGUF".toByteArray(Charsets.US_ASCII))
        buf.putInt(3) // version
        buf.putLong(0) // tensor count
        buf.putLong(2) // metadata kv count
        // kv 1: general.architecture = STRING "llama"
        buf.putLong(archKey.size.toLong())
        buf.put(archKey)
        buf.putInt(8) // GGUF_TYPE_STRING
        buf.putLong(arch.size.toLong())
        buf.put(arch)
        // kv 2: llama.context_length = UINT32 4096
        buf.putLong(ctxKey.size.toLong())
        buf.put(ctxKey)
        buf.putInt(4) // GGUF_TYPE_UINT32
        buf.putInt(4096)
        return buf.array().copyOf(buf.position())
    }

    @Test
    fun ggufMetadataArchitectureAndContextAreParsed() {
        val header = parseGgufHeader(sampleGguf())
        assertEquals("llama", header?.architecture)
        assertEquals(4096L, header?.contextLength)
    }

    @Test
    fun truncatedGgufKeepsMagicVersionResult() {
        val truncated = sampleGguf().copyOf(20)
        val header = parseGgufHeader(truncated)
        assertEquals(3, header?.version)
        assertNull(header?.architecture)
    }

    @Test
    fun inspectFileReadsArchitectureFromDisk() {
        val file = File.createTempFile("mmodel-test", ".gguf")
        try {
            file.writeBytes(sampleGguf())
            val result = ModelInspector.inspectFile(file)
            assertEquals("llama", result.architecture)
            assertEquals(ChatTemplateFamily.LLAMA3, result.templateFamily)
        } finally {
            file.delete()
        }
    }

    @Test
    fun nonGgufHeaderIsNull() {
        assertEquals(null, parseGgufHeader(ByteArray(8)))
    }

    @Test
    fun qwenMapsToChatML() {
        assertEquals(ChatTemplateFamily.CHATML, templateFamilyForArchitecture("qwen2"))
        assertEquals(ChatTemplateFamily.CHATML, templateFamilyForArchitecture("qwen3"))
    }

    @Test
    fun llamaMapsByVersion() {
        assertEquals(ChatTemplateFamily.LLAMA3, templateFamilyForArchitecture("llama"))
        assertEquals(ChatTemplateFamily.LLAMA2, templateFamilyForArchitecture("llama2"))
    }

    @Test
    fun textGgufIsChatReady() {
        val result = inspectFromName("qwen2.5-3b-instruct-q4_k_m.gguf", 1, "qwen2", 8192)
        assertEquals(ModelFormat.GGUF, result.format)
        assertEquals(ModelKind.TEXT, result.kind)
        assertEquals(CompatibilityVerdict.CAN_RUN_TEXT_CHAT, result.verdict)
        assertEquals(ChatTemplateFamily.CHATML, result.templateFamily)
    }

    @Test
    fun projectorIsNeedsPair() {
        val result = inspectFromName("mmproj-model-f16.gguf", 1)
        assertEquals(CompatibilityVerdict.NEEDS_PAIR, result.verdict)
    }

    @Test
    fun safetensorsIsReferenceOnly() {
        val result = inspectFromName("model.safetensors", 1)
        assertEquals(CompatibilityVerdict.REFERENCE_ONLY, result.verdict)
    }
}
