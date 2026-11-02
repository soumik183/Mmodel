package com.m.model.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ModelFilesTest {
    @Test
    fun detectsCommonWeightFormats() {
        assertEquals(ModelFormat.GGUF, ModelFiles.formatOf("model-Q4_K_M.gguf"))
        assertEquals(ModelFormat.SAFETENSORS, ModelFiles.formatOf("model.safetensors"))
        assertEquals(ModelFormat.PYTORCH, ModelFiles.formatOf("pytorch_model.bin"))
        assertEquals(ModelFormat.ONNX, ModelFiles.formatOf("model.onnx"))
        assertEquals(ModelFormat.TFLITE, ModelFiles.formatOf("model.tflite"))
        assertEquals(ModelFormat.COREML, ModelFiles.formatOf("model.mlpackage"))
        assertEquals(ModelFormat.TENSORFLOW, ModelFiles.formatOf("model.h5"))
        assertEquals(ModelFormat.OTHER, ModelFiles.formatOf("notes.txt"))
    }

    @Test
    fun classifiesModelKindsFromNames() {
        assertEquals(ModelKind.PROJECTOR, ModelFiles.kindOf("mmproj-model-f16.gguf", ModelFormat.GGUF))
        assertEquals(ModelKind.STT, ModelFiles.kindOf("whisper-large-v3.gguf", ModelFormat.GGUF))
        assertEquals(ModelKind.TTS, ModelFiles.kindOf("kokoro-vits.onnx", ModelFormat.ONNX))
        assertEquals(ModelKind.IMAGE, ModelFiles.kindOf("flux-schnell.safetensors", ModelFormat.SAFETENSORS))
        assertEquals(ModelKind.VISION, ModelFiles.kindOf("llava-v1.6-vision.gguf", ModelFormat.GGUF))
        assertEquals(ModelKind.EMBEDDING, ModelFiles.kindOf("bge-small.safetensors", ModelFormat.SAFETENSORS))
        assertEquals(ModelKind.TEXT, ModelFiles.kindOf("qwen2.5-7b-instruct.gguf", ModelFormat.GGUF))
    }

    @Test
    fun onlySingleFileTextGgufIsChatReady() {
        assertNull(ModelFiles.chatBlocker(ModelFormat.GGUF, ModelKind.TEXT, fileCount = 1))
        assertNotNull(ModelFiles.chatBlocker(ModelFormat.SAFETENSORS, ModelKind.TEXT, fileCount = 1))
        assertNotNull(ModelFiles.chatBlocker(ModelFormat.GGUF, ModelKind.TEXT, fileCount = 2))
        assertNotNull(ModelFiles.chatBlocker(ModelFormat.GGUF, ModelKind.PROJECTOR, fileCount = 1))
        assertNotNull(ModelFiles.chatBlocker(ModelFormat.GGUF, ModelKind.TTS, fileCount = 1))
        assertNotNull(ModelFiles.chatBlocker(ModelFormat.GGUF, ModelKind.STT, fileCount = 1))
    }
}
