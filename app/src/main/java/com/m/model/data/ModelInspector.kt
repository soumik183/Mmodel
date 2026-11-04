package com.m.model.data

import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

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
        val base = inspectFromName(file.name, 1, parsed?.architecture, parsed?.contextLength)
        return if (parsed == null && base.format == ModelFormat.GGUF) {
            base.copy(verdict = CompatibilityVerdict.BAD_FILE, reason = "This file is not a valid GGUF model.")
        } else base
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
    if (bytes.size < 24 || version !in 2..3) {
        // Header magic only; there is no metadata to scan.
        return ParsedGgufHeader(version, null, null, null)
    }
    // Best effort: truncation or corruption stops the scan and keeps what was found.
    val scan = runCatching { scanGgufMetadata(bytes) }.getOrNull()
    return ParsedGgufHeader(version, scan?.architecture, scan?.contextLength, scan?.tensorCount)
}

private data class GgufScan(
    val architecture: String?,
    val contextLength: Long?,
    val tensorCount: Long?,
)

private fun scanGgufMetadata(bytes: ByteArray): GgufScan {
    var tensorCount: Long? = null
    var architecture: String? = null
    var contextLength: Long? = null
    val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    buf.position(8)
    tensorCount = buf.long
    var remainingKv = buf.long
    while (remainingKv > 0 && buf.remaining() >= 8) {
        if (architecture != null && contextLength != null) break
        remainingKv--
        val key = readGgufString(buf) ?: break
        if (buf.remaining() < 4) break
        val type = buf.int
        if (type == GGUF_TYPE_STRING) {
            val value = readGgufString(buf) ?: break
            if (key == "general.architecture") architecture = value
        } else if (contextLength == null && key.endsWith(".context_length") && isGgufIntegerType(type)) {
            contextLength = readGgufInteger(buf, type) ?: break
        } else if (!skipGgufValue(buf, type)) {
            break
        }
    }
    return GgufScan(architecture, contextLength, tensorCount)
}

private const val GGUF_TYPE_STRING = 8
private const val GGUF_TYPE_ARRAY = 9

private fun isGgufIntegerType(type: Int) = type == 2 || type == 3 || type == 4 || type == 5 || type == 10 || type == 11

private fun ggufScalarSize(type: Int): Long? = when (type) {
    0, 1, 7 -> 1
    2, 3 -> 2
    4, 5, 6 -> 4
    10, 11, 12 -> 8
    else -> null
}

private fun readGgufString(buf: ByteBuffer): String? {
    if (buf.remaining() < 8) return null
    val len = buf.long
    if (len < 0 || len > buf.remaining()) return null
    val text = String(buf.array(), buf.position(), len.toInt(), Charsets.UTF_8)
    buf.position(buf.position() + len.toInt())
    return text
}

private fun readGgufInteger(buf: ByteBuffer, type: Int): Long? = when (type) {
    2, 3 -> if (buf.remaining() >= 2) buf.short.toLong() else null
    4, 5 -> if (buf.remaining() >= 4) (buf.int.toLong() and 0xFFFFFFFFL) else null
    10, 11 -> if (buf.remaining() >= 8) buf.long else null
    else -> null
}

private fun skipGgufValue(buf: ByteBuffer, type: Int): Boolean {
    if (type == GGUF_TYPE_STRING) return readGgufString(buf) != null
    if (type == GGUF_TYPE_ARRAY) {
        if (buf.remaining() < 12) return false
        val elemType = buf.int
        val count = buf.long
        // Every element is at least one byte, so count cannot exceed the remaining buffer.
        if (count < 0 || count > buf.remaining()) return false
        val elemSize = ggufScalarSize(elemType)
        if (elemSize != null) {
            val bytes = elemSize * count
            if (bytes > buf.remaining()) return false
            buf.position(buf.position() + bytes.toInt())
            return true
        }
        var i = 0L
        while (i < count) {
            if (!skipGgufValue(buf, elemType)) return false
            i++
        }
        return true
    }
    val size = ggufScalarSize(type) ?: return false
    if (buf.remaining() < size) return false
    buf.position(buf.position() + size.toInt())
    return true
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
