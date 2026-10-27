package com.m.model.data

data class ChatTurn(
    val role: String,
    val content: String,
)

object ChatPrompt {
    const val DEFAULT_SYSTEM =
        "You are Mmodel, a concise assistant running locally on an Android phone. " +
            "Answer helpfully and clearly. Keep answers short unless the user asks for detail."

    private const val MAX_HISTORY_CHARS = 6_000

    fun trimHistory(turns: List<ChatTurn>, maxChars: Int = MAX_HISTORY_CHARS): List<ChatTurn> {
        if (turns.isEmpty()) return emptyList()
        if (turns.sumOf { it.content.length } <= maxChars) return withoutLeadingAssistant(turns)

        val kept = ArrayDeque<ChatTurn>()
        var chars = 0
        for (turn in turns.asReversed()) {
            if (kept.isNotEmpty() && chars + turn.content.length > maxChars) break
            kept.addFirst(turn)
            chars += turn.content.length
        }

        if (kept.isEmpty()) {
            val last = turns.last()
            return listOf(
                last.copy(content = last.content.take(maxChars.coerceAtLeast(1))),
            )
        }

        return withoutLeadingAssistant(kept.toList())
    }

    fun nativeMessages(
        system: String,
        turns: List<ChatTurn>,
    ): List<Map<String, Any>> = buildList {
        if (system.isNotBlank()) {
            add(mapOf("role" to "system", "content" to system))
        }
        turns.forEach { turn ->
            add(mapOf("role" to turn.role, "content" to turn.content))
        }
    }

    fun fallback(
        modelKey: String,
        system: String,
        turns: List<ChatTurn>,
    ): String {
        val key = modelKey.lowercase()
        return when {
            key.contains("gemma") -> gemma(system, turns)
            key.contains("llama-3") || key.contains("llama_3") || key.contains("llama3") ->
                llama3(system, turns)

            key.contains("phi-3") || key.contains("phi3") -> phi3(system, turns)
            key.contains("mistral") || key.contains("mixtral") ||
                key.contains("llama-2") || key.contains("llama2") -> mistral(system, turns)

            else -> chatml(system, turns)
        }
    }

    private fun gemma(system: String, turns: List<ChatTurn>): String {
        val sb = StringBuilder()
        var firstUser = true
        turns.forEach { turn ->
            when (turn.role) {
                "user" -> {
                    sb.append("<start_of_turn>user\n")
                    if (firstUser && system.isNotBlank()) {
                        sb.append(system).append("\n\n")
                    }
                    sb.append(turn.content).append("<end_of_turn>\n")
                    firstUser = false
                }

                "assistant" -> sb.append("<start_of_turn>model\n")
                    .append(turn.content).append("<end_of_turn>\n")
            }
        }
        return sb.append("<start_of_turn>model\n").toString()
    }

    private fun llama3(system: String, turns: List<ChatTurn>): String {
        val sb = StringBuilder("<|begin_of_text|>")
        fun header(role: String, content: String, close: Boolean = true) {
            sb.append("<|start_header_id|>").append(role).append("<|end_header_id|>\n\n")
                .append(content)
            if (close) sb.append("<|eot_id|>")
        }

        if (system.isNotBlank()) header("system", system)
        turns.forEach { turn ->
            when (turn.role) {
                "user" -> header("user", turn.content)
                "assistant" -> header("assistant", turn.content)
            }
        }
        header("assistant", "", close = false)
        return sb.toString()
    }

    private fun phi3(system: String, turns: List<ChatTurn>): String {
        val sb = StringBuilder()
        if (system.isNotBlank()) {
            sb.append("<|system|>\n").append(system).append("<|end|>\n")
        }
        turns.forEach { turn ->
            when (turn.role) {
                "user" -> sb.append("<|user|>\n").append(turn.content).append("<|end|>\n")
                "assistant" -> sb.append("<|assistant|>\n").append(turn.content).append("<|end|>\n")
            }
        }
        return sb.append("<|assistant|>\n").toString()
    }

    private fun mistral(system: String, turns: List<ChatTurn>): String {
        val sb = StringBuilder()
        var firstUser = true
        turns.forEach { turn ->
            when (turn.role) {
                "user" -> {
                    sb.append("<s>[INST] ")
                    if (firstUser && system.isNotBlank()) {
                        sb.append(system).append("\n\n")
                    }
                    sb.append(turn.content).append(" [/INST]")
                    firstUser = false
                }

                "assistant" -> sb.append(" ").append(turn.content).append("</s>")
            }
        }
        return sb.toString()
    }

    private fun chatml(system: String, turns: List<ChatTurn>): String {
        val sb = StringBuilder()
        if (system.isNotBlank()) {
            sb.append("<|im_start|>system\n").append(system).append("<|im_end|>\n")
        }
        turns.forEach { turn ->
            when (turn.role) {
                "user" -> sb.append("<|im_start|>user\n").append(turn.content)
                    .append("<|im_end|>\n")

                "assistant" -> sb.append("<|im_start|>assistant\n").append(turn.content)
                    .append("<|im_end|>\n")
            }
        }
        return sb.append("<|im_start|>assistant\n").toString()
    }

    private fun withoutLeadingAssistant(turns: List<ChatTurn>): List<ChatTurn> {
        val firstUser = turns.indexOfFirst { it.role == "user" }
        return if (firstUser <= 0) turns else turns.drop(firstUser)
    }
}
