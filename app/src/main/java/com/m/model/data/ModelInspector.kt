package com.m.model.data

import java.io.File
import java.io.FileInputStream

enum class ChatTemplateFamily { CHATML, LLAMA3, LLAMA2, GEMMA, MISTRAL, PHI, DEEPSEEK, FALCON, CODE, DEFAULT }

enum class CompatibilityVerdict { CAN_RUN_TEXT_CHAT, NEEDS_PAIR, REFERENCE_ONLY, UNSUPPORTED_HERE, BAD_FILE }

data class ModelInspection(
    val format: ModelFormat,
    val kind: ModelKind,
    val architecture: String?,
    val contextLength: Long?,
    val templateFamily: ChatTemplateFamily,
    val verdict: CompatibilityVerdict,
    val reason: String?,
)

object ModelInspector {
    private const val MAX_INSPECT_BYTES = 4 * 1024 * 1024

    fun inspectFile(file: File): ModelInspection {
        val parsed = runCatching {
            FileInputStream(file).use { input ->
                val buf = ByteArray(MAX_INSPECT_BYTES)
                var read = 0
                while (read < buf.size) {
                    val n = input.read(buf, read, buf.size - read)
                    if (n <= 0) break
                    read += n
                }
                parseGgufHeader(buf.copyOf(read.coerceAtLeast(8)))
            }
        }.getOrNull()
        return if (parsed == null) {
            val base = inspectFromName(file.name, 1)
            if (base.format == ModelFormat.GGUF) {
                base.copy(verdict = CompatibilityVerdict.BAD_FILE, reason = "This file is not a valid GGUF model.")
            } else base
        } else {
            inspectFromName(file.name, 1)
        }
    }

    fun templateFamilyForArchitecture(architecture: String?): ChatTemplateFamily {
        val a = architecture?.lowercase().orEmpty()
        return when {
            a.contains("qwen") -> ChatTemplateFamily.CHATML
            a.contains("deepseek") -> ChatTemplateFamily.DEEPSEEK
            a.contains("gemma") || a.contains("codegemma") -> ChatTemplateFamily.GEMMA
            a.contains("llama3") || a == "llama" -> ChatTemplateFamily.LLAMA3
            a.contains("llama2") -> ChatTemplateFamily.LLAMA2
            a.contains("mistral") || a.contains("mixtral") -> ChatTemplateFamily.MISTRAL
            a.contains("phi") -> ChatTemplateFamily.PHI
            a.contains("falcon") -> ChatTemplateFamily.FALCON
            a.contains("starcoder") || a.contains("code") -> ChatTemplateFamily.CODE
            a.isBlank() -> ChatTemplateFamily.DEFAULT
            else -> ChatTemplateFamily.DEFAULT
        }
    }

    fun inspectFromName(
        fileName: String,
        fileCount: Int,
        architecture: String? = null,
        contextLength: Long? = null,
    ): ModelInspection {
        val format = ModelFiles.formatOf(fileName)
        val kind = ModelFiles.kindOf(fileName, format)
        val verdict = when {
            format != ModelFormat.GGUF -> CompatibilityVerdict.REFERENCE_ONLY
            kind == ModelKind.PROJECTOR -> CompatibilityVerdict.NEEDS_PAIR
            kind == ModelKind.TTS || kind == ModelKind.STT ||
                kind == ModelKind.IMAGE || kind == ModelKind.EMBEDDING ->
                CompatibilityVerdict.UNSUPPORTED_HERE
            fileCount > 1 -> CompatibilityVerdict.UNSUPPORTED_HERE
            else -> CompatibilityVerdict.CAN_RUN_TEXT_CHAT
        }
        val reason = ModelFiles.chatBlocker(format, kind, fileCount)
        return ModelInspection(
            format = format,
            kind = kind,
            architecture = architecture,
            contextLength = contextLength,
            templateFamily = templateFamilyForArchitecture(architecture),
            verdict = verdict,
            reason = reason,
        )
    }
}

data class ParsedGgufHeader(val version: Int, val architecture: String?, val contextLength: Long?, val tensorCount: Long?)

internal fun parseGgufHeader(bytes: ByteArray): ParsedGgufHeader? {
    if (bytes.size < 8) return null
    if (String(bytes, 0, 4, Charsets.US_ASCII) != "GGUF") return null
    val version = (bytes[4].toInt() and 0xFF) or
        ((bytes[5].toInt() and 0xFF) shl 8) or
        ((bytes[6].toInt() and 0xFF) shl 16) or
        ((bytes[7].toInt() and 0xFF) shl 24)
    return ParsedGgufHeader(version, null, null, null)
}

fun templateFamilyForArchitecture(architecture: String?): ChatTemplateFamily =
    ModelInspector.templateFamilyForArchitecture(architecture)

fun inspectFromName(
    fileName: String,
    fileCount: Int,
    architecture: String? = null,
    contextLength: Long? = null,
): ModelInspection = ModelInspector.inspectFromName(fileName, fileCount, architecture, contextLength)

fun ModelInspection.userMessage(): String = when (verdict) {
    CompatibilityVerdict.CAN_RUN_TEXT_CHAT -> "Ready to chat in Mmodel."
    CompatibilityVerdict.NEEDS_PAIR -> "This is a vision projector (mmproj), not a chat model. Import the matching GGUF weights."
    CompatibilityVerdict.REFERENCE_ONLY -> "Chat runs GGUF models only. Convert this with llama.cpp (convert_hf_to_gguf.py)."
    CompatibilityVerdict.UNSUPPORTED_HERE -> reason ?: "This model type cannot run in Mmodel chat yet."
    CompatibilityVerdict.BAD_FILE -> "This file is not a valid GGUF model."
}
