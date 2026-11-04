# Mmodel Chat Runner Advanced Design

Date: 2026-11-04  
Branch intent: bounded-implementation-after-spec on `main`  
Topic: chat + model runner — inspection, compatibility, device optimization, chat UX

## 1. Goal

Make the on-device model runner feel and behave like a high-quality local LLM product:

- support as many safe GGUF chat models as possible;
- correctly classify, inspect, and explain chat / vision / speech / image / embedding / non-GGUF models;
- load faster and run faster by adapting to the device;
- optimize for speed, battery/RAM, and adaptive balance depending on user choice;
- improve chat reliability and conversation UX;
- never claim runtime support for model families the bundled engine cannot actually execute.

## 2. Reality check / decomposition

The current app ships one native runtime: `io.github.ljcamargo:llamacpp-kotlin`, which exposes a text/LLM GGUF chat engine. It can run chat-capable single-file GGUF text models. It does **not** by itself provide:

- vision input (images in prompt, mmproj / vision projector pairing);
- speech generation (TTS) or speech recognition (ASR/STT);
- image generation;
- ONNX / TFLite / TensorFlow / CoreML / Safetensors / PyTorch inference;
- sharded GGUF loading;
- embedding-vector search.

Those are separate runtime subsystems. This milestone therefore does **not** add fake support. Instead it does the robust foundation that lets all later runtimes plug in:

- a model inspection layer that can distinguish all common model types cleanly;
- a compatibility matrix that explicitly says what can run now, what is reference-only, and what needs conversion or another runtime;
- a faster, device-aware GGUF chat runner for everything the current engine truly supports;
- chat UX parity with modern mobile chat apps.

Later milestones can add: vision runner, speech runner, image runner, ONNX/TFLite runner. Each gets its own spec → plan → implementation cycle.

## 3. Proposed architecture

### 3.1 Model inspection (`ModelInspector`)

New pure-Kotlin layer (no Compose, no Android framework beyond optional file reading helpers):

Responsibilities:

- read GGUF header safely (magic + version);
- parse GGUF metadata block where safe/practical:
  - `general.architecture`
  - `general.name`
  - `general.quantization_version` if present
  - `general.file_type` if present
  - `llama.context_length` / analogous context keys if exposed by llama.cpp metadata naming
  - `tokenizer.ggml.model`
  - tensor count / parameter estimate where derivable without loading the whole model;
- map architecture → chat template family:
  - Qwen / ChatML
  - Llama 3 / Llama 2
  - Gemma
  - Mistral / Mixtral
  - Phi / Phi-3
  - DeepSeek
  - Falcon
  - StarCoder / CodeGemma / code chat variants
  - fallback ChatML;
- produce a `ModelInspection` result:
  - container format;
  - model kind (text / vision / projector / TTS / STT / image / embedding / other);
  - architecture string;
  - context length;
  - estimated parameter / tensor hints where available;
  - chat-template family;
  - chat readiness verdict;
  - actionable reason if blocked.

Current file-name heuristics in `ModelFiles` remain a fallback for imports with mangled names, but inspection wins when metadata is available.

### 3.2 Compatibility matrix (`ModelCompatibility`)

A single source of truth for “what can this device do with this file?”:

- `CanRunTextChat` — single-file GGUF text model, arm64 device, enough RAM, valid GGUF v≥2;
- `ReferenceOnly` — Safetensors, PyTorch, ONNX, TFLite, CoreML, TensorFlow;
- `NeedsPair` — vision projector (mmproj) that requires a matching GGUF;
- `UnsupportedHere` — TTS voice GGUF, STT GGUF, image-generation GGUF, embedding GGUF, sharded GGUF;
- `BadFile` — not GGUF / invalid header / ancient version / unreadable.

UI copy should use this matrix everywhere: Downloads, Detail, Chat header, Folder inspector. One function, one message, no drift.

### 3.3 Device optimization profiles

Replace today’s single “default threads / fixed context” model with an explicit profile:

- **Speed**: more threads (up to cores-1 or cores on big-core-heavy devices, capped sensibly), larger batch, context up to 4096 when RAM allows, prefer `use_mmap = true` when safe, minimal bootstrap work.
- **Battery / RAM**: fewer threads (big cores minus margin), smaller batch, smaller context (1024–2048), prefer slower-but-cooler settings, earlier unload hints.
- **Adaptive**: default. Chooses Speed or Battery/RAM from total RAM, free RAM, core count, and recent fit. Stores user override. Shows chosen profile in Chat (`Auto · 6 threads · 2048 ctx`).

Profile logic lives alongside `DeviceSpecs` as pure functions so it is unit-testable without Android.

### 3.4 Faster / more reliable load path

Changes to `LlamaEngine`:

- validate header on the opened fd (already partially present) and keep that as a fast fail;
- avoid duplicate work between `MainViewModel` RAM fit and engine load;
- use `use_mmap = true` by default on supported devices for faster model mapping and lower load latency (with fallback to false only if load fails for mmap-specific errors);
- deterministic session IDs (no `Random.nextInt()` per load);
- keep `operation` and `generation` locks, but move token emission off lock once accepted — tokens are just callbacks and do not need to be serialized;
- add `warmup` lightweight text-tokenization/formatting step only when cheap; never block first token on optional warmup;
- expose `modelDetails` + inspection summary on `Loaded` so UI can show architecture / context / template used.

### 3.5 Chat-quality engine improvements

Keep native `getFormattedChat` as primary path (it uses the model’s own template), improve fallbacks:

- architecture detection order: metadata architecture → saved architecture → repo gguf architecture → model id/key → default ChatML;
- add Qwen / DeepSeek / Falcon / Llama-3.1 stop-token discipline: generated text is trimmed at first stop marker, not flooded with template artifacts;
- sampling tuned by profile:
  - Speed: slightly higher `n_predict`, lower `penalty_last_n`;
  - Battery: lower `n_predict`, tighter context;
  - Adaptive: neutral;
- `min_p` / `top_k` / `top_p` tuned per template family (ChatML vs Gemma vs Llama header models behave differently);
- reply cleanup: collapse runaway repetition artifacts, trim trailing partial template tags, cap visible garbage after EOS.

### 3.6 Chat UX polish

All within existing `ChatScreen` / `MainViewModel` state machine:

- safe paste of long prompts (history budget by tokens approximation, not chars only);
- token-aware history compaction: keep newest turns within `contextLength * 0.7`; preserve system prompt; never truncate mid-turn without marking it;
- continue answer (append to last assistant reply), regenerate (existing), copy, clear, undo-last-exchange (pop last user+assistant pair);
- streaming cursor already exists: keep blinking cursor, plus tok/s indicator already present;
- failed generations keep partial text and offer Continue/Retry, not a blank slate;
- model header shows architecture · context · threads · profile;
- loading panel steps should align with real phases: Validate → Map file → Init context → Warmup → Ready.

### 3.7 Error handling

Uniform failure taxonomy:

- `MissingFile`, `BadHeader`, `OldGgufVersion`, `WrongAbi`, `OutOfMemory`, `UnsafeStorage`, `UnavailableImportedUri`, `EngineRejected`, `GenerationStopped`;
- each maps to a user sentence + a next action (“Download again”, “Import again”, “Pick smaller quant”, “Free RAM”, “Open Settings”);
- SAF imports re-check GGUF validity at load time (already implemented at import) and emit `BadHeader` instead of a native crash.

## 4. Data flow

```
Download/Import → ModelFiles format/kind (fast filename heuristics)
                → ModelInspector (metadata when available)
                → ModelCompatibility verdict (single source)
                → Chat load gate (device.canRunLocalChat + verdict + ramFit)
                → LlamaEngine.load(profile, context, threads)
                → ChatPrompt.format (native template or architecture fallback)
                → LlamaEngine.generate(profile sampling)
                → streaming tokens → UI (cursor, tok/s, meta)
```

## 5. Performance targets (explicit, no fake numbers)

- Time-to-first-token should improve vs. current build on the same device/model because of mmap + thread/profile + reduced pre-token work.
- Long conversations should degrade gracefully via token-budgeted history compaction instead of hitting context hard.
- Failed loads must be fast (header/metadata fast-fail before allocating a context).

## 6. Testing plan

- Unit tests:
  - inspection mapping: architecture → template family;
  - compatibility matrix: every format/kind/fileCount combination;
  - device profiles: RAM/cores → threads/context choices;
  - history compaction by token budget;
  - sampling params by profile;
  - stop-token cleanup.
- Existing tests must stay green: `ChatPromptTest`, `ModelFitTest`, `ModelFilesTest`, `LocalModelTest`.
- CI: `gradle :app:testDebugUnitTest :app:assembleDebug` must pass. No local build.

## 7. What is deliberately out of scope

- running vision/TTS/STT/image/ONNX/TFLite models in this milestone;
- multi-shard GGUF loading;
- editing downloaded model files;
- account, analytics, sync, cloud inference;
- wallpaper/dynamic-color redesign (already shipped).

## 8. Risks

- GGUF metadata keys vary across exports; parser must fail open to filename heuristics, never crash.
- `use_mmap = true` can behave differently on some kernels; keep it behind profile choice and make load errors actionable.
- More aggressive threads can throttle on sustained generation; Adaptive caps by core count and leaves headroom.
- Deep inspection adds binary parsing surface; keep it read-only, bounded (e.g. first N MB), and allocation-light.

## 9. Spec self-review

- No placeholders: every module has a named owner function.
- Consistency: compatibility matrix is consistent with `ModelFiles.chatBlocker` today and with the engine’s actual capabilities.
- Scope: this milestone fits one implementation plan; vision/audio/image runtimes are explicitly later milestones.
- Ambiguity removed: “support all types” is defined as inspect + classify + explain + run what the engine truly supports, not fake multi-runtime support.

## 10. User review gate

Please review this spec and tell me to proceed or what to change before I write the implementation plan.
