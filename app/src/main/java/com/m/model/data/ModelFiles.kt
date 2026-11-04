package com.m.model.data

import androidx.compose.runtime.Immutable
import java.util.Locale

/**
 * Container formats Mmodel can store. Only GGUF is executable by the bundled
 * llama.cpp chat engine; the rest are importable for reference or conversion.
 */
@Immutable
enum class ModelFormat(val label: String, val chatRunnable: Boolean = false) {
    GGUF("GGUF", chatRunnable = true),
    SAFETENSORS("Safetensors"),
    PYTORCH("PyTorch"),
    ONNX("ONNX"),
    TFLITE("TFLite"),
    COREML("CoreML"),
    TENSORFLOW("TensorFlow"),
    OTHER("Unknown"),
}

/** Best-effort model family, used to label imports and explain what they do. */
@Immutable
enum class ModelKind(val label: String) {
    TEXT("Text"),
    VISION("Vision"),
    TTS("Text to speech"),
    STT("Speech to text"),
    IMAGE("Image"),
    EMBEDDING("Embeddings"),
    PROJECTOR("Vision projector"),
    OTHER("Unknown"),
}

/**
 * File-name detection for imported models. Extensions are what Android
 * providers reliably expose; magic bytes are validated separately for GGUF.
 */
object ModelFiles {
    fun formatOf(fileName: String): ModelFormat {
        val name = fileName.lowercase(Locale.ROOT).substringAfterLast('/')
        return when {
            name.endsWith(".gguf") -> ModelFormat.GGUF
            name.endsWith(".safetensors") -> ModelFormat.SAFETENSORS
            name.endsWith(".bin") ||
                name.endsWith(".pt") ||
                name.endsWith(".pth") ||
                name.endsWith(".ckpt") -> ModelFormat.PYTORCH
            name.endsWith(".onnx") -> ModelFormat.ONNX
            name.endsWith(".tflite") || name.endsWith(".lite") -> ModelFormat.TFLITE
            name.endsWith(".mlmodel") ||
                name.endsWith(".mlpackage") ||
                name.endsWith(".mlmodelc") -> ModelFormat.COREML
            name.endsWith(".h5") ||
                name.endsWith(".keras") ||
                name.endsWith(".pb") -> ModelFormat.TENSORFLOW
            else -> ModelFormat.OTHER
        }
    }

    fun isSupported(fileName: String): Boolean = formatOf(fileName) != ModelFormat.OTHER

    fun kindOf(fileName: String, format: ModelFormat): ModelKind {
        val name = fileName.lowercase(Locale.ROOT)
        fun has(vararg keys: String) = keys.any { name.contains(it) }

        return when {
            has("mmproj", "projector", "vision_encoder", "vision-encoder") -> ModelKind.PROJECTOR
            has(
                "whisper", "wav2vec", "wav2letter", "parakeet", "asr",
                "speech-to-text", "speech_to_text",
            ) -> ModelKind.STT
            has(
                "tts", "vits", "piper", "kokoro", "bark", "tacotron", "vocoder",
                "speecht5", "speech-t5", "xtts", "matcha",
            ) -> ModelKind.TTS
            has(
                "stable-diffusion", "sdxl", "sd3", "flux", "diffusion", "unet",
                "controlnet", "vae",
            ) -> ModelKind.IMAGE
            has(
                "clip", "siglip", "llava", "vision", "vit-", "dinov", "moondream",
                "pixtral", "minicpm-v",
            ) -> ModelKind.VISION
            has("embed", "bge-", "gte-", "e5-", "minilm", "sentence-transformers") ->
                ModelKind.EMBEDDING
            else -> ModelKind.TEXT
        }
    }

    /** Why this file cannot be loaded into chat, or null when it can. */
    fun chatBlocker(format: ModelFormat, kind: ModelKind, fileCount: Int): String? {
        val verdict = ModelInspector.inspectFromName("model", fileCount)
            .let { it } // placeholder-free: derive verdict from format/kind directly below
        val v = when {
            format != ModelFormat.GGUF -> CompatibilityVerdict.REFERENCE_ONLY
            kind == ModelKind.PROJECTOR -> CompatibilityVerdict.NEEDS_PAIR
            kind == ModelKind.TTS || kind == ModelKind.STT ||
                kind == ModelKind.IMAGE || kind == ModelKind.EMBEDDING ->
                CompatibilityVerdict.UNSUPPORTED_HERE
            fileCount > 1 -> CompatibilityVerdict.UNSUPPORTED_HERE
            else -> CompatibilityVerdict.CAN_RUN_TEXT_CHAT
        }
        return if (v == CompatibilityVerdict.CAN_RUN_TEXT_CHAT) null
        else ModelInspector.inspectFromName("", fileCount)
            .copy(verdict = v, reason = null, format = format, kind = kind)
            .userMessage()
    }
}
