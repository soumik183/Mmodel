package com.m.model.data

import org.junit.Assert.assertEquals
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
