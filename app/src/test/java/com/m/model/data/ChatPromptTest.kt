package com.m.model.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatPromptTest {
    private val turns = listOf(
        ChatTurn("user", "Hello"),
        ChatTurn("assistant", "Hi"),
        ChatTurn("user", "What is a GGUF?"),
    )

    @Test
    fun historyBudgetKeepsNewestWithinContext() {
        val long = (1..10).map { ChatTurn("user", "x".repeat(500)) }
        val trimmed = ChatPrompt.trimForContext(long, contextTokens = 2048)
        assertTrue(trimmed.sumOf { it.content.length } <= 2048 * 3)
        assertEquals("user", trimmed.first().role)
    }

    @Test
    fun qwenFamilyUsesChatML() {
        val prompt = ChatPrompt.fallbackForFamily(ChatTemplateFamily.CHATML, "sys", turns)
        assertTrue(prompt.contains("<|im_start|>system"))
    }

    @Test
    fun cleanGenerationTrimsStopArtifacts() {
        assertEquals("Hello", ChatPrompt.cleanGeneration("Hello<|im_end|>\n<trash>"))
    }

    @Test
    fun deepseekFamilyDoesNotUseGemmaMarkers() {
        val prompt = ChatPrompt.fallbackForFamily(ChatTemplateFamily.DEEPSEEK, "sys", turns)
        assertFalse(prompt.contains("<start_of_turn>"))
    }

    @Test
    fun speedSamplingIsMoreGenerousThanBattery() {
        val speed = ChatPrompt.samplingParams(PerformanceProfile.SPEED)
        val battery = ChatPrompt.samplingParams(PerformanceProfile.BATTERY_RAM)
        assertTrue((speed["n_predict"] as Int) >= (battery["n_predict"] as Int))
        assertTrue((speed["penalty_last_n"] as Int) <= (battery["penalty_last_n"] as Int))
    }

    @Test
    fun chatmlIncludesSystemAndAssistantPrefix() {
        val prompt = ChatPrompt.fallback(
            "org/Qwen2.5-3B-Instruct-GGUF",
            "Be brief.",
            turns,
        )

        assertTrue(prompt.contains("<|im_start|>system\nBe brief.<|im_end|>"))
        assertTrue(prompt.contains("<|im_start|>user\nHello<|im_end|>"))
        assertTrue(prompt.endsWith("<|im_start|>assistant\n"))
    }

    @Test
    fun llama3UsesHeaderTemplate() {
        val prompt = ChatPrompt.fallback(
            "meta-llama/Llama-3.2-1B-Instruct-GGUF",
            "Be brief.",
            turns,
        )

        assertTrue(prompt.startsWith("<|begin_of_text|>"))
        assertTrue(prompt.contains("<|start_header_id|>user<|end_header_id|>"))
        assertTrue(prompt.endsWith("<|start_header_id|>assistant<|end_header_id|>\n\n"))
    }

    @Test
    fun gemmaMergesSystemIntoFirstUserTurn() {
        val prompt = ChatPrompt.fallback(
            "google/gemma-3-1b-it-GGUF",
            "Be brief.",
            turns,
        )

        assertTrue(prompt.startsWith("<start_of_turn>user"))
        assertTrue(prompt.contains("Be brief."))
        assertFalse(prompt.contains("system"))
        assertTrue(prompt.endsWith("<start_of_turn>model\n"))
    }

    @Test
    fun trimKeepsRecentTurnsAndStartsWithUser() {
        val long = listOf(
            ChatTurn("user", "a".repeat(100)),
            ChatTurn("assistant", "b".repeat(100)),
            ChatTurn("user", "c".repeat(100)),
            ChatTurn("assistant", "d".repeat(100)),
            ChatTurn("user", "latest"),
        )

        val trimmed = ChatPrompt.trimHistory(long, maxChars = 120)

        assertEquals("user", trimmed.first().role)
        assertEquals("latest", trimmed.last().content)
        assertTrue(trimmed.sumOf { it.content.length } <= 120)
    }

    @Test
    fun mistralEmitsBosOnceAndAlternatingTurns() {
        val prompt = ChatPrompt.fallback(
            "mistralai/Mistral-7B-Instruct-v0.2",
            "Be brief.",
            turns,
        )

        assertEquals(1, "<s>".toRegex().findAll(prompt).count())
        assertTrue(prompt.startsWith("<s>[INST] "))
        assertTrue(prompt.contains("Hello [/INST]"))
        assertTrue(prompt.contains("</s>[INST] What is a GGUF? [/INST]"))
    }

    @Test
    fun oversizedLastMessageKeepsTheTail() {
        val long = listOf(ChatTurn("user", "a".repeat(200) + " THE QUESTION"))

        val trimmed = ChatPrompt.trimHistory(long, maxChars = 100)

        assertEquals(1, trimmed.size)
        assertEquals(100, trimmed.single().content.length)
        assertTrue(trimmed.single().content.endsWith("THE QUESTION"))
    }

    @Test
    fun nativeMessagesIncludeRolesInOrder() {
        val messages = ChatPrompt.nativeMessages("System", turns)

        assertEquals("system", messages.first()["role"])
        assertEquals("user", messages[1]["role"])
        assertEquals("assistant", messages[2]["role"])
    }
}
