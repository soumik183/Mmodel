# Mmodel

**Mmodel = mobile model.** Find AI models your Android phone can actually run, download them, and chat on-device.

Mmodel reads your phone's real specifications and the actual files published on [Hugging Face](https://huggingface.co), then labels a model with an estimated fit:

- **Works** — comfortable inside your safe RAM budget
- **Slow** — likely to load, but tight on memory
- **Won't run** — too large for this phone (or not enough free storage)
- **Unknown** — the repo does not publish file sizes

Choose a GGUF quantization, download it with visible progress and a real path, then chat locally. Prompts and replies never leave the device.

## Features

- **Live device profile** — RAM, free storage, CPU cores, ABI, chipset, OpenGL ES version, Android version, and whether local chat is supported
- **Hugging Face search** — 50 results per request, sort by trending / likes / recently updated, GGUF filter
- **Real-data fit estimate** — actual GGUF file sizes (multi-shard quants summed), per-quant fit badges, RAM overhead, and free-storage checks
- **Model vs phone comparison** — side-by-side memory, download and local-chat checks using the same numbers as the verdict
- **Recommended for you** — chat-ready GGUF picks size-checked against this phone's RAM and free storage
- **Model details** — parameters, context length, downloadable quants, downloads, likes, tags, and a direct Hugging Face link
- **Downloads** — choose a quantization, see the exact app-private folder and file sizes, pause/resume with HTTP Range, delete with confirmation, and browse downloaded models
- **Local chat** — llama.cpp inference on `arm64-v8a`, streaming tokens, stop generation, clear conversation, unload model, and a conservative 2K context
- **Material 3 UI** — custom modern corner shapes, letter avatars, gradients, light and dark themes
- No accounts, no analytics, no tracking. Only the `INTERNET` permission.

## Install the APK

Every push to `main` builds a debug APK in GitHub Actions.

**Direct download (latest):**
[app-debug.apk](https://github.com/soumik183/Mmodel/releases/download/latest/app-debug.apk)

Or open the [latest release page](https://github.com/soumik183/Mmodel/releases/tag/latest), or download the `Mmodel-debug-apk` artifact from the [Actions tab](https://github.com/soumik183/Mmodel/actions).

> The APK is debug-signed and includes only the `arm64-v8a` native engine. Android may warn about installing an unknown app; allow install from your browser/files app.
>
> **Requires a 64-bit ARM Android phone (Android 8.0 / API 26 or newer).**

## Downloads and storage

Models are stored in app-specific external storage:

```
/Android/data/com.m.model/files/models/<author>_<model>/<file>.gguf
```

The Downloads tab shows the absolute folder path and a copy button. Files survive app restarts and can be paused/resumed. They are removed when you uninstall Mmodel.

- One download runs at a time.
- Partial files are kept for Resume.
- Multi-shard GGUF quants are downloaded together. Local chat currently supports **single-file GGUFs only** because the bundled runtime loads one model file descriptor.
- Gated/private Hugging Face repos return HTTP 401/403; accept access on the website first. A token is not stored by the app.

## Local chat

Chat loads a downloaded GGUF through `io.github.ljcamargo:llamacpp-kotlin` 0.2.0 (`llama.cpp`, `arm64-v8a`). It uses a 2K context, 4 threads when available, and streams tokens into the conversation.

- Models marked **Won't run** are blocked from loading to avoid native out-of-memory crashes.
- **Slow** models can be loaded, with a warning to keep the conversation short.
- The model's own chat template is requested from the GGUF first; a family fallback (ChatML, Llama 3, Gemma, Phi-3, Mistral) is used if it is unavailable.
- Unload the model when done to free RAM.
- The first load can take a while; reading several GB from flash is expected.

## How the fit estimate works

1. Read the **actual published GGUF file sizes** in the repo. All shards of one quantization are summed; projector, LoRA, importance-matrix, MTP and tokenizer helper files are ignored. For non-GGUF repos, safetensors / PyTorch / ONNX weight sizes are summed.
2. Present one download option per quantization with its own fit badge.
3. Estimate runtime RAM: `file size × 1.15 + 600 MB` (GGUF) or `file size × 1.2 + 900 MB` (other formats), to leave room for the KV cache and runtime.
4. Compare with a safe budget: `min(total RAM − 1.5 GB, 75% of total RAM)`.
   - `≤ 65%` of budget → **Works**
   - `≤ 100%` → **Slow**
   - `> 100%` → **Won't run**
5. If the download plus 300 MB does not fit in free storage → **Won't run**.
6. The model screen repeats the exact comparison: estimated RAM vs the safe budget, download size vs free storage (including the 300 MB reserve), and GGUF layout vs the arm64 llama.cpp runtime. Context is shown alongside the app's 2K local-chat cap.
7. The device card shows live RAM and storage and has a **Recheck** action.

Quantization, context length, background apps, thermals and the runtime all change real performance. **This is a guide, not a benchmark.**

## Build

The project standardizes on:

- Kotlin 2.2.20, AGP 8.7.3, Gradle 8.9, JDK 17
- compileSdk / targetSdk 35, minSdk 26
- Jetpack Compose Material 3 (BOM 2024.12.01)
- `llamacpp-kotlin` 0.2.0, packaged for `arm64-v8a`

CI uses `gradle/actions/setup-gradle` with Gradle 8.9, so no Gradle wrapper is committed.

Locally, with a JDK and Android SDK installed:

```bash
gradle :app:testDebugUnitTest :app:assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Project layout

```
app/src/main/java/com/m/model/
  MainActivity.kt
  data/           Hugging Face models + API, device specs, fit logic,
                  download store, chat prompt, llama.cpp engine
  ui/             ViewModel, navigation, formatting
  ui/components/  Letter avatars, badges, cards, rows
  ui/screens/     Home, Explore, Model detail, Downloads, Chat
  ui/theme/       Material 3 theme and shapes
app/src/test/     JVM tests for fit, quantization parsing, chat prompts
.github/workflows/android.yml
```

## Notes / limitations

- Hugging Face's public API is used without a token. Heavy use can be rate-limited (HTTP 429).
- The Explore screen loads the top 50 matches per query; the leading cards are enriched with full file sizes, and any remaining card loads its exact sizes when opened.
- Only published weight-file sizes are used. Repos without sizes show **Unknown**.
- Local chat is experimental and limited to single-file GGUFs on `arm64-v8a`.
- Gated/private models cannot be downloaded through the app; the link opens Hugging Face.
- Debug APK only. A release build would need signing.
