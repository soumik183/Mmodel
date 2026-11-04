# Chat Runner Advanced Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make Mmodel's GGUF chat runner inspect models deeply, classify all model types honestly, optimize load/generation per device, and feel like a modern local chat product.

**Architecture:** Pure-Kotlin inspection + compatibility + profile layers feed the existing `LlamaEngine` and `MainViewModel` chat flow; UI only renders verdicts and state already computed in the ViewModel. No new native runtimes in this milestone.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, coroutines, kotlinx serialization, existing `llamacpp-kotlin` chat engine, JUnit 4 unit tests, GitHub Actions CI (`gradle :app:testDebugUnitTest :app:assembleDebug`).

**Spec:** `docs/superpowers/specs/2026-11-04-chat-runner-advanced-design.md`

## Global Constraints

- minSdk 26, targetSdk 35, compileSdk 35, arm64-v8a only for engine.
- No local gradle/APK builds; CI is the only compiler/tester.
- No fake runtime support: only single-file GGUF text chat runs now; vision/TTS/STT/image/ONNX etc. are inspect/classify/explain only.
- All user-visible failure messages must be actionable (what happened + next action).
- GGUF parsing must be bounded, read-only, and fail open to filename heuristics.
- Keep existing tests green: `ChatPromptTest`, `ModelFitTest`, `ModelFilesTest`, `LocalModelTest`.

---

### Task 1: Inspection data model + architecture→template mapping

**Files:**
- Create: `app/src/main/java/com/m/model/data/ModelInspector.kt`
- Modify: none
- Test: `app/src/test/java/com/m/model/data/ModelInspectorTest.kt`

**Interfaces:**
- Consumes: nothing.
- Produces:
  - `data class ModelInspection(format: ModelFormat, kind: ModelKind, architecture: String?, contextLength: Long?, templateFamily: ChatTemplateFamily, verdict: CompatibilityVerdict, reason: String?)`
  - `enum class ChatTemplateFamily { CHATML, LLAMA3, LLAMA2, GEMMA, MISTRAL, PHI, DEEPSEEK, FALCON, CODE, DEFAULT }`
  - `fun templateFamilyForArchitecture(architecture: String?): ChatTemplateFamily`
  - `fun inspectFromName(fileName: String, fileCount: Int, architecture: String? = null, contextLength: Long? = null): ModelInspection`

- [ ] **Step 1: Write failing tests**

```kotlin
package com.m.model.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ModelInspectorTest {
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
```

- [ ] **Step 2: Run tests and verify failure**

Run: `./gradlew :app:testDebugUnitTest --tests "*ModelInspectorTest"` (via CI if local gradle unavailable)
Expected: compilation failure / unresolved references for `ModelInspector` types.

- [ ] **Step 3: Implement minimal `ModelInspector.kt`**

```kotlin
package com.m.model.data

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

fun templateFamilyForArchitecture(architecture: String?): ChatTemplateFamily =
    ModelInspector.templateFamilyForArchitecture(architecture)

fun inspectFromName(
    fileName: String,
    fileCount: Int,
    architecture: String? = null,
    contextLength: Long? = null,
): ModelInspection = ModelInspector.inspectFromName(fileName, fileCount, architecture, contextLength)
```

- [ ] **Step 4: Run tests and verify pass**

Run: CI unit tests for `ModelInspectorTest`
Expected: all new tests PASS, existing `ModelFilesTest` still PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/m/model/data/ModelInspector.kt app/src/test/java/com/m/model/data/ModelInspectorTest.kt
git commit -m "feat(chat): add model inspection model and template mapping"
```

---

### Task 2: Bounded GGUF metadata parser

**Files:**
- Modify: `app/src/main/java/com/m/model/data/ModelInspector.kt`
- Modify: `app/src/main/java/com/m/model/data/LlamaEngine.kt` (use inspection header path instead of duplicated read)
- Test: `app/src/test/java/com/m/model/data/ModelInspectorTest.kt`

**Interfaces:**
- Consumes: `ModelInspection`, `ChatTemplateFamily` from Task 1.
- Produces:
  - `fun inspectFile(file: File): ModelInspection` in `ModelInspector` (bounded to first 4 MiB, fail-open)
  - `internal fun parseGgufHeader(bytes: ByteArray): ParsedGgufHeader?`
  - `data class ParsedGgufHeader(version: Int, architecture: String?, contextLength: Long?, tensorCount: Long?)`

- [ ] **Step 1: Write failing tests**

```kotlin
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
```

- [ ] **Step 2: Run and verify failure**

Run: CI `ModelInspectorTest`
Expected: unresolved `parseGgufHeader`.

- [ ] **Step 3: Implement bounded parser**

```kotlin
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
```

In `ModelInspector.inspectFile(file: File)`: read only first 4 MiB via `FileInputStream`, call `parseGgufHeader`, if null return `inspectFromName(file.name, 1)` with `BAD_FILE` verdict when name says `.gguf` but magic fails. Never throw; on any exception fall back to `inspectFromName`.

In `LlamaEngine.validateGgufHeader`: delegate to the same header logic (no second parser). Keep `Os.pread` path for `ParcelFileDescriptor`.

- [ ] **Step 4: Run tests**

Expected: `ModelInspectorTest` PASS; existing `LocalModelTest` PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/m/model/data/ModelInspector.kt app/src/main/java/com/m/model/data/LlamaEngine.kt app/src/test/java/com/m/model/data/ModelInspectorTest.kt
git commit -m "feat(chat): add bounded GGUF header inspection"
```

---

### Task 3: Compatibility matrix as single source of truth

**Files:**
- Modify: `app/src/main/java/com/m/model/data/ModelInspector.kt`
- Modify: `app/src/main/java/com/m/model/data/ModelFiles.kt` (delegate `chatBlocker` to inspector verdict text)
- Modify: `app/src/main/java/com/m/model/data/DownloadStore.kt` (`DownloadRecord.chatBlocker` uses inspector)
- Test: `app/src/test/java/com/m/model/data/ModelFilesTest.kt`

**Interfaces:**
- Consumes: `ModelInspection`, `CompatibilityVerdict` from Task 1.
- Produces:
  - `fun ModelInspection.userMessage(): String`
  - `fun DownloadRecord.chatBlocker(): String?` rewritten to use `ModelInspector.inspectFromName(...)`

- [ ] **Step 1: Write failing tests**

```kotlin
    @Test
    fun compatibilityMatrixCoversAllKinds() {
        assertEquals(CompatibilityVerdict.CAN_RUN_TEXT_CHAT, inspectFromName("model-q4_k_m.gguf", 1).verdict)
        assertEquals(CompatibilityVerdict.REFERENCE_ONLY, inspectFromName("model.onnx", 1).verdict)
        assertEquals(CompatibilityVerdict.UNSUPPORTED_HERE, inspectFromName("voice-tts.gguf", 1).verdict)
        assertEquals(CompatibilityVerdict.UNSUPPORTED_HERE, inspectFromName("model-00001-of-00002.gguf", 2).verdict)
    }
```

- [ ] **Step 2: Run and verify failure**

Run: CI `ModelFilesTest`
Expected: new expectations fail or are not yet encoded.

- [ ] **Step 3: Implement matrix**

```kotlin
fun ModelInspection.userMessage(): String = when (verdict) {
    CompatibilityVerdict.CAN_RUN_TEXT_CHAT -> "Ready to chat in Mmodel."
    CompatibilityVerdict.NEEDS_PAIR -> "This is a vision projector (mmproj), not a chat model. Import the matching GGUF weights."
    CompatibilityVerdict.REFERENCE_ONLY -> "Chat runs GGUF models only. Convert this with llama.cpp (convert_hf_to_gguf.py)."
    CompatibilityVerdict.UNSUPPORTED_HERE -> reason ?: "This model type cannot run in Mmodel chat yet."
    CompatibilityVerdict.BAD_FILE -> "This file is not a valid GGUF model."
}
```

`ModelFiles.chatBlocker(...)` delegates to `ModelInspector.inspectFromName(...)` and returns `null` only for `CAN_RUN_TEXT_CHAT`.

`DownloadRecord.chatBlocker` calls `ModelFiles.chatBlocker(format, kind, files.size)` as today, but now the text comes from the matrix.

- [ ] **Step 4: Run tests**

Expected: all PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/m/model/data/ModelInspector.kt app/src/main/java/com/m/model/data/ModelFiles.kt app/src/test/java/com/m/model/data/ModelFilesTest.kt
git commit -m "feat(chat): unify compatibility verdicts in one matrix"
```

---

### Task 4: Device optimization profiles

**Files:**
- Create: `app/src/main/java/com/m/model/data/DeviceProfile.kt`
- Modify: `app/src/main/java/com/m/model/data/DeviceSpecs.kt` (expose `profileHint` helpers only if needed; keep `inferenceBudgetBytes` unchanged)
- Test: `app/src/test/java/com/m/model/data/DeviceProfileTest.kt`

**Interfaces:**
- Consumes: `DeviceSpecs`.
- Produces:
  - `enum class PerformanceProfile { SPEED, BATTERY_RAM, ADAPTIVE }`
  - `data class ResolvedProfile(profile: PerformanceProfile, threads: Int, contextLength: Int, batchSize: Int, useMmap: Boolean, label: String)`
  - `fun resolveProfile(device: DeviceSpecs, requested: PerformanceProfile, modelContext: Long? = null): ResolvedProfile`
  - `fun threadsFor(device: DeviceSpecs, profile: PerformanceProfile): Int`
  - `fun contextFor(device: DeviceSpecs, profile: PerformanceProfile, modelContext: Long?): Int`

- [ ] **Step 1: Write failing tests**

```kotlin
package com.m.model.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceProfileTest {
    private fun device(ramGb: Long, cores: Int, freeRamGb: Long = ramGb / 2) = DeviceSpecs(
        brand = "Test", model = "Phone", androidVersion = "Android 15", soc = "Test",
        abi = "arm64-v8a", cpuCores = cores, glEsVersion = "3.2",
        totalRamBytes = ramGb * 1_000_000_000L, availableRamBytes = freeRamGb * 1_000_000_000L,
        freeStorageBytes = 64_000_000_000L, totalStorageBytes = 128_000_000_000L,
    )

    @Test fun speedUsesMoreThreads() {
        val r = resolveProfile(device(8, 8), PerformanceProfile.SPEED, 8192)
        assertTrue(r.threads >= 6)
        assertEquals(4096, r.contextLength)
    }

    @Test fun batteryUsesFewerThreadsAndSmallerContext() {
        val r = resolveProfile(device(4, 8), PerformanceProfile.BATTERY_RAM, 8192)
        assertTrue(r.threads <= 4)
        assertTrue(r.contextLength <= 2048)
    }

    @Test fun adaptiveRespectsLowRam() {
        val r = resolveProfile(device(3, 6), PerformanceProfile.ADAPTIVE, 8192)
        assertTrue(r.contextLength <= 2048)
    }

    @Test fun contextNeverExceedsModelContext() {
        val r = resolveProfile(device(16, 8), PerformanceProfile.SPEED, 1024)
        assertEquals(1024, r.contextLength)
    }
}
```

- [ ] **Step 2: Run and verify failure**

Run: CI `DeviceProfileTest`
Expected: unresolved profile types.

- [ ] **Step 3: Implement profiles**

```kotlin
package com.m.model.data

enum class PerformanceProfile { SPEED, BATTERY_RAM, ADAPTIVE }

data class ResolvedProfile(
    val profile: PerformanceProfile,
    val threads: Int,
    val contextLength: Int,
    val batchSize: Int,
    val useMmap: Boolean,
    val label: String,
)

object DeviceProfile {
    fun resolve(device: DeviceSpecs, requested: PerformanceProfile, modelContext: Long? = null): ResolvedProfile {
        val effective = when (requested) {
            PerformanceProfile.ADAPTIVE -> if (device.totalRamBytes < 6_000_000_000L) PerformanceProfile.BATTERY_RAM else PerformanceProfile.SPEED
            else -> requested
        }
        val threads = threadsFor(device, effective)
        val context = contextFor(device, effective, modelContext)
        val batch = if (effective == PerformanceProfile.SPEED) 512 else 256
        val mmap = true
        val label = "${threads} threads · ${context / 1024}K ctx"
        return ResolvedProfile(effective, threads, context, batch, mmap, label)
    }

    fun threadsFor(device: DeviceSpecs, profile: PerformanceProfile): Int {
        val cores = device.cpuCores.coerceAtLeast(1)
        return when (profile) {
            PerformanceProfile.SPEED -> (cores - 1).coerceIn(2, cores)
            PerformanceProfile.BATTERY_RAM -> (cores / 2).coerceIn(1, 4)
            PerformanceProfile.ADAPTIVE -> (cores - 2).coerceIn(2, 6)
        }
    }

    fun contextFor(device: DeviceSpecs, profile: PerformanceProfile, modelContext: Long?): Int {
        val cap = when (profile) {
            PerformanceProfile.SPEED -> 4096
            PerformanceProfile.BATTERY_RAM -> 2048
            PerformanceProfile.ADAPTIVE -> if (device.totalRamBytes >= 8_000_000_000L) 4096 else 2048
        }
        val wanted = (modelContext ?: cap.toLong()).coerceIn(512L, cap.toLong())
        return wanted.toInt()
    }
}

fun resolveProfile(device: DeviceSpecs, requested: PerformanceProfile, modelContext: Long? = null) =
    DeviceProfile.resolve(device, requested, modelContext)

fun threadsFor(device: DeviceSpecs, profile: PerformanceProfile) = DeviceProfile.threadsFor(device, profile)

fun contextFor(device: DeviceSpecs, profile: PerformanceProfile, modelContext: Long?) =
    DeviceProfile.contextFor(device, profile, modelContext)
```

- [ ] **Step 4: Run tests**

Expected: all PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/m/model/data/DeviceProfile.kt app/src/test/java/com/m/model/data/DeviceProfileTest.kt
git commit -m "feat(chat): add device performance profiles"
```

---

### Task 5: LlamaEngine profile-aware load + generation

**Files:**
- Modify: `app/src/main/java/com/m/model/data/LlamaEngine.kt`
- Modify: `app/src/main/java/com/m/model/data/DeviceProfile.kt` (only if `SamplingParams` is added here; otherwise put sampling in `ChatPrompt.kt`)
- Test: existing `ModelFitTest`, `LocalModelTest` must stay green; add `LlamaEngineTest` only for pure helpers if added

**Interfaces:**
- Consumes: `DeviceSpecs`, `ResolvedProfile` from Task 4, `ModelInspection` from Task 1.
- Produces:
  - `suspend fun load(file: File, profile: ResolvedProfile): Loaded`
  - `suspend fun load(descriptor: ParcelFileDescriptor, modelName: String, profile: ResolvedProfile): Loaded`
  - `data class Loaded(contextLength: Int, threads: Int, modelDetails: Map<String, Any>, profileLabel: String, architecture: String?)`
  - `suspend fun generate(prompt: String, maxTokens: Int, profile: PerformanceProfile, onToken: (String) -> Unit): GenerationResult`

- [ ] **Step 1: Write failing test (pure sampling helper)**

Add to `ChatPromptTest.kt`:

```kotlin
    @Test
    fun speedSamplingIsMoreGenerousThanBattery() {
        val speed = ChatPrompt.samplingParams(PerformanceProfile.SPEED)
        val battery = ChatPrompt.samplingParams(PerformanceProfile.BATTERY_RAM)
        assertTrue(speed["n_predict"] as Int >= battery["n_predict"] as Int)
        assertTrue(speed["penalty_last_n"] as Int <= battery["penalty_last_n"] as Int)
    }
```

Requires `PerformanceProfile` import in test.

- [ ] **Step 2: Run and verify failure**

Run: CI `ChatPromptTest`
Expected: unresolved `samplingParams`.

- [ ] **Step 3: Implement**

In `LlamaEngine`:
- keep old `load(file, contextLength)` overloads as deprecated delegating to profile overloads with `ADAPTIVE` profile so existing call sites compile;
- new load uses `profile.threads`, `profile.contextLength`, `profile.batchSize`, `"use_mmap" to profile.useMmap`;
- deterministic session id: `System.nanoTime().absoluteValue` instead of `Random.nextInt()`;
- `generate` merges `ChatPrompt.samplingParams(profile)` into completion map instead of hardcoded values.

In `ChatPrompt`:
- add `fun samplingParams(profile: PerformanceProfile): Map<String, Any>` returning temperature/top-k/top-p/min-p/penalty values per profile.

- [ ] **Step 4: Run tests**

Expected: all PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/m/model/data/LlamaEngine.kt app/src/main/java/com/m/model/data/ChatPrompt.kt app/src/test/java/com/m/model/data/ChatPromptTest.kt
git commit -m "feat(chat): profile-aware engine load and sampling"
```

---

### Task 6: Architecture-aware prompts + stop cleanup

**Files:**
- Modify: `app/src/main/java/com/m/model/data/ChatPrompt.kt`
- Test: `app/src/test/java/com/m/model/data/ChatPromptTest.kt`

**Interfaces:**
- Consumes: `ChatTemplateFamily` from Task 1, `ModelInspection` from Task 1.
- Produces:
  - `fun fallbackForFamily(family: ChatTemplateFamily, system: String, turns: List<ChatTurn>): String`
  - `fun cleanGeneration(text: String): String`
  - `fun samplingParams(profile: PerformanceProfile): Map<String, Any>` (from Task 5)

- [ ] **Step 1: Write failing tests**

```kotlin
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
```

- [ ] **Step 2: Run and verify failure**

Run: CI `ChatPromptTest`
Expected: unresolved `fallbackForFamily` / `cleanGeneration`.

- [ ] **Step 3: Implement**

- `fallbackForFamily` maps: `CHATML, DEEPSEEK, DEFAULT → chatml()`, `LLAMA3 → llama3()`, `LLAMA2 → mistral()` (legacy), `GEMMA → gemma()`, `PHI → phi3()`, `MISTRAL → mistral()`, `FALCON, CODE → chatml()`.
- `fallback(modelKey, ...)` derives family via `templateFamilyForArchitecture(architectureFromKey(modelKey))` then delegates.
- `cleanGeneration`: cut at first of `<|im_end|>`, `<|end_of_turn|>`, `<|eot_id|>`, `</s>`, trim, collapse 3+ blank lines to 2.

- [ ] **Step 4: Run tests**

Expected: all PASS including old `ChatPromptTest` cases.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/m/model/data/ChatPrompt.kt app/src/test/java/com/m/model/data/ChatPromptTest.kt
git commit -m "feat(chat): architecture-aware prompt families and cleanup"
```

---

### Task 7: ViewModel wiring — profiles, inspection, history budget, continue/undo

**Files:**
- Modify: `app/src/main/java/com/m/model/ui/MainViewModel.kt`
- Modify: `app/src/main/java/com/m/model/data/DownloadStore.kt` (only if `DownloadRecord` needs `architecture`/`contextLength` already present — verify, do not change schema unless needed)
- Test: `app/src/test/java/com/m/model/data/ChatPromptTest.kt` for history budget helper if added there

**Interfaces:**
- Consumes: `ModelInspection`, `ResolvedProfile`, `ChatPrompt.samplingParams`, `LlamaEngine.load(profile)`.
- Produces (ViewModel public API):
  - `var performanceProfile: PerformanceProfile`
  - `fun setPerformanceProfile(profile: PerformanceProfile)`
  - `fun continueLastAnswer()`
  - `fun undoLastExchange()`
  - `val chat: StateFlow<ChatState>` extended with `profileLabel: String`, `architecture: String?`, `templateFamily: String?`

- [ ] **Step 1: Write failing test for history budget**

Add pure helper in `ChatPrompt.kt` first (testable without Android):

```kotlin
    @Test
    fun historyBudgetKeepsNewestWithinContext() {
        val long = (1..10).map { ChatTurn("user", "x".repeat(500)) }
        val trimmed = ChatPrompt.trimForContext(long, contextTokens = 2048)
        assertTrue(trimmed.sumOf { it.content.length } <= 2048 * 3)
        assertEquals("user", trimmed.first().role)
    }
```

- [ ] **Step 2: Run and verify failure**

Run: CI `ChatPromptTest`
Expected: unresolved `trimForContext`.

- [ ] **Step 3: Implement**

- `ChatPrompt.trimForContext(turns, contextTokens)`: budget = `contextTokens * 3` chars (approx 3 chars/token), keep newest, preserve user-first, reuse `trimHistory` logic.
- `MainViewModel`:
  - on `selectChatRecord` / `loadChatModel`: inspect file via `ModelInspector.inspectFile(File(path))` or filename fallback; compute `resolveProfile(device, performanceProfile, inspection.contextLength ?: record.contextLength)`; pass profile to `engine.load`.
  - store `profileLabel`/`architecture`/`templateFamily` into `_chat`.
  - `runGeneration`: use `trimForContext` with loaded `contextLength`; use `cleanGeneration` on final text before persisting meta.
  - `continueLastAnswer()`: appends continuation prompt to last assistant message and regenerates with "Continue: " prefix.
  - `undoLastExchange()`: removes last user+assistant pair (2 messages) if present.
  - `setPerformanceProfile`: persists to `SharedPreferences("chat")`, reloads model if ready.

- [ ] **Step 4: Run tests**

Expected: all PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/m/model/ui/MainViewModel.kt app/src/main/java/com/m/model/data/ChatPrompt.kt app/src/test/java/com/m/model/data/ChatPromptTest.kt
git commit -m "feat(chat): wire profiles, inspection, history budget, continue/undo"
```

---

### Task 8: Chat UI polish (header, load panel, errors, actions)

**Files:**
- Modify: `app/src/main/java/com/m/model/ui/screens/ChatScreen.kt`
- Modify: `app/src/main/java/com/m/model/ui/screens/DownloadsScreen.kt` (only if verdict copy changes are needed)
- Modify: `app/src/main/java/com/m/model/ui/screens/FolderScreen.kt` (use new verdict copy)
- Test: none (UI verified by CI compile + existing unit tests)

**Interfaces:**
- Consumes: `ChatState.profileLabel`, `architecture`, `templateFamily` from Task 7; `ModelInspection.userMessage()` from Task 3.
- Produces: UI rendering only.

- [ ] **Step 1: Implement**

- `ChatHeader`: subtitle becomes `"${inspection.architecture ?: quant} · ${contextLength} ctx · ${threads} threads · ${profileLabel}"` when loaded; otherwise current model name + quant.
- `ModelLoadingPanel`: steps aligned to real phases already shipped; no change to the looping logic.
- Error banner: show `ModelInspection.userMessage()` text; dismiss button already exists.
- Message actions row: keep Copy + Regenerate; add Continue (shown on last assistant message) wired to `vm.continueLastAnswer()`.
- Overflow menu (model picker dropdown): add Undo last exchange wired to `vm.undoLastExchange()`.
- Profile selector: segmented control (Speed / Balanced / Battery) in `ChatHeader` next to model picker; calls `vm.setPerformanceProfile(...)` and reloads if ready.

- [ ] **Step 2: Run CI**

Run: GitHub Actions `gradle :app:testDebugUnitTest :app:assembleDebug`
Expected: compile PASS (no unresolved `continueLastAnswer` / `undoLastExchange` / `setPerformanceProfile`).

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/m/model/ui/screens/ChatScreen.kt
git commit -m "feat(chat): polish header, actions, profile selector"
```

---

### Task 9: CI green + release verification

**Files:**
- Modify: none (verification only)

- [ ] **Step 1: Push to `main`**

```bash
git push origin main
```

- [ ] **Step 2: Watch CI**

Run: `gh run list --branch main --limit 3` (poll until `completed`)
Expected: `conclusion: success` on the pushed HEAD.

- [ ] **Step 3: Verify release asset**

Run: `gh release view latest --json assets --jq '.assets[] | {name: .name, updated: .updatedAt}'`
Expected: fresh `app-release.apk` uploaded after the green run.

- [ ] **Step 4: Report**

Report commit SHA, CI run id, and release asset timestamp. Note that JS/Compose visual verification requires a physical device.

---

## Self-Review

- Spec coverage: inspection (Tasks 1–2), compatibility (Task 3), device profiles (Task 4), engine optimization (Task 5), chat quality (Task 6), chat UX (Tasks 7–8), honest non-support for other runtimes (Task 3), testing/CI (Task 9). All spec sections mapped.
- No placeholders: every step includes concrete test code, implementation sketches, run commands, expected results, and commit commands.
- Type consistency: `ModelInspection`, `ChatTemplateFamily`, `CompatibilityVerdict`, `PerformanceProfile`, `ResolvedProfile`, `LlamaEngine.load(profile)`, `ChatPrompt.samplingParams(profile)`, `ChatPrompt.fallbackForFamily`, `ChatPrompt.cleanGeneration`, `ChatPrompt.trimForContext`, `MainViewModel.continueLastAnswer/undoLastExchange/setPerformanceProfile` are defined once and reused consistently.
