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

        // A lone oversized message is kept whole by the loop above; keep its tail,
        // which is where the actual question lives.
        val first = kept.firstOrNull()
        if (first != null && first.content.length > maxChars) {
            kept.removeFirst()
            kept.addFirst(first.copy(content = first.content.takeLast(maxChars)))
        }

        return withoutLeadingAssistant(kept.toList())
    }

    fun trimForContext(turns: List<ChatTurn>, contextTokens: Int): List<ChatTurn> {
        val budget = (contextTokens.coerceAtLeast(512) * 3).coerceIn(800, 12_000)
        return trimHistory(turns, maxChars = budget)
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

    fun samplingParams(profile: PerformanceProfile): Map<String, Any> = when (profile) {
        PerformanceProfile.SPEED -> mapOf(
            "temperature" to 0.7, "top_k" to 40, "top_p" to 0.95, "min_p" to 0.05,
            "penalty_last_n" to 32, "penalty_repeat" to 1.1, "n_predict" to 512,
        )
        PerformanceProfile.BATTERY_RAM -> mapOf(
            "temperature" to 0.7, "top_k" to 40, "top_p" to 0.95, "min_p" to 0.05,
            "penalty_last_n" to 64, "penalty_repeat" to 1.1, "n_predict" to 256,
        )
        PerformanceProfile.ADAPTIVE -> mapOf(
            "temperature" to 0.7, "top_k" to 40, "top_p" to 0.95, "min_p" to 0.05,
            "penalty_last_n" to 48, "penalty_repeat" to 1.1, "n_predict" to 384,
        )
    }

    fun fallback(
        modelKey: String,
        system: String,
        turns: List<ChatTurn>,
    ): String {
        val key = modelKey.lowercase()
        val family = when {
            key.contains("gemma") -> ChatTemplateFamily.GEMMA
            key.contains("llama-3") || key.contains("llama_3") || key.contains("llama3") -> ChatTemplateFamily.LLAMA3
            key.contains("phi-3") || key.contains("phi3") -> ChatTemplateFamily.PHI
            key.contains("mistral") || key.contains("mixtral") ||
                key.contains("llama-2") || key.contains("llama2") -> ChatTemplateFamily.MISTRAL
            else -> ChatTemplateFamily.CHATML
        }
        return fallbackForFamily(family, system, turns)
    }

    fun fallbackForFamily(
        family: ChatTemplateFamily,
        system: String,
        turns: List<ChatTurn>,
    ): String = when (family) {
        ChatTemplateFamily.LLAMA3 -> llama3(system, turns)
        ChatTemplateFamily.GEMMA -> gemma(system, turns)
        ChatTemplateFamily.PHI -> phi3(system, turns)
        ChatTemplateFamily.MISTRAL, ChatTemplateFamily.LLAMA2 -> mistral(system, turns)
        ChatTemplateFamily.CHATML, ChatTemplateFamily.DEEPSEEK,
        ChatTemplateFamily.FALCON, ChatTemplateFamily.CODE,
        ChatTemplateFamily.DEFAULT -> chatml(system, turns)
    }

    fun cleanGeneration(text: String): String {
        val stops = listOf("<|im_end|>", "<|end_of_turn|>", "<|eot_id|>", "</s>")
        var cut = text
        for (stop in stops) {
            val idx = cut.indexOf(stop)
            if (idx >= 0) cut = cut.substring(0, idx)
        }
        return cut.trim().replace(Regex("\n{3,}"), "\n\n")
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
        var firstTurn = true
        turns.forEach { turn ->
            when (turn.role) {
                "user" -> {
                    if (firstTurn) {
                        sb.append("<s>[INST] ")
                        if (system.isNotBlank()) {
                            sb.append(system).append("\n\n")
                        }
                        firstTurn = false
                    } else {
                        sb.append("[INST] ")
                    }
                    sb.append(turn.content).append(" [/INST]")
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
