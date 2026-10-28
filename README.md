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
- **Hugging Face search** — 20 results per page, automatic infinite scroll, sort by trending / likes / recently updated, and category filters
- **Categories** — All, GGUF, Chat, Vision, Audio and Embeddings; all task categories are constrained to GGUF so they can be downloaded and chatted with
- **Real-data fit estimate** — actual GGUF file sizes (multi-shard quants summed), per-quant fit badges, RAM overhead, and free-storage checks
- **Model vs phone comparison** — side-by-side memory, download and local-chat checks using the same numbers as the verdict
- **Recommended for you** — chat-ready GGUF picks size-checked against this phone's RAM and free storage, streamed in as soon as each real size arrives
- **Model details** — parameters, context length, downloadable quants, downloads, likes, tags, and a direct Hugging Face link
- **Downloads** — choose a quantization, see the exact app-private folder and file sizes, pause/resume with HTTP Range, delete with confirmation, and browse downloaded models
- **Import from storage** — pick a single-file GGUF with Android's document picker. It loads through a file descriptor, so the original file is not copied into app storage
- **Download guardrails** — warns when a quant is Slow / Won't run / missing a reported size, is sharded, or is not chat-ready before the download starts
- **Local chat** — llama.cpp inference on `arm64-v8a`, streaming tokens, stop generation, clear conversation, unload model, and a conservative 2K context
- **Premium Material 3 UI** — a translucent glass navigation pill, animated selection states, aurora indigo/teal palette, adaptive launcher icon, custom squircle letter avatars, and light/dark themes
- **Light on startup** — Explore loads the first 20 matches on first visit, while recommendations appear progressively instead of waiting for every size lookup
- No accounts, no analytics, no tracking. Only the `INTERNET` permission.

## Install the APK

Every push to `main` builds and tests in GitHub Actions. When release signing secrets are configured, the workflow verifies and publishes a signed release APK; otherwise it falls back to a debug APK so CI keeps working.

**Direct download (latest):**
[app-release.apk](https://github.com/soumik183/Mmodel/releases/download/latest/app-release.apk)

Or open the [latest release page](https://github.com/soumik183/Mmodel/releases/tag/latest), or download the `Mmodel-signed-apk` / `Mmodel-debug-apk` artifact from the [Actions tab](https://github.com/soumik183/Mmodel/actions).

> The APK includes only the `arm64-v8a` native engine. Android may warn about installing an unknown app; allow install from your browser/files app.
>
> **Requires a 64-bit ARM Android phone (Android 8.0 / API 26 or newer).**
>
> Switching from a debug build to a release-signed build requires uninstalling the debug app first, because the signing keys differ.

## Downloads and storage

Models are stored in app-specific external storage:

```
/Android/data/com.m.model/files/models/<author>_<model>/<file>.gguf
```

The Downloads tab shows the absolute folder path and a copy button. Files survive app restarts and can be paused/resumed. They are removed when you uninstall Mmodel.

- One download runs at a time.
- Partial files are kept for Resume.
- Multi-shard GGUF quants are downloaded together. Local chat currently supports **single-file GGUFs only** because the bundled runtime loads one model file descriptor.
- **Imported GGUFs** stay in their original folder and are read through a persisted Android Storage Access Framework URI. They are not copied into app storage. Deleting the imported record in Mmodel does not delete the original file. If a provider revokes access after a restart, import the file again.
- Gated/private Hugging Face repos return HTTP 401/403; accept access on the website first. A token is not stored by the app.

## Local chat

Chat loads a downloaded or imported GGUF through `io.github.ljcamargo:llamacpp-kotlin` 0.2.0 (`llama.cpp`, `arm64-v8a`). It uses a 2K context, 4 threads when available, and streams tokens into the conversation.

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

### Release signing

Create a keystore once, keep it private, and add these repository secrets (`Settings → Secrets and variables → Actions`):

| Secret | Value |
| --- | --- |
| `KEYSTORE_BASE64` | Base64-encoded keystore file |
| `KEYSTORE_PASSWORD` | Keystore password |
| `KEY_ALIAS` | Key alias |
| `KEY_PASSWORD` | Key password |

```bash
keytool -genkeypair -v -keystore mmodel-release.jks \
  -alias mmodel -keyalg RSA -keysize 2048 -validity 10000
base64 -w0 mmodel-release.jks > keystore.b64
gh secret set KEYSTORE_BASE64 < keystore.b64
gh secret set KEYSTORE_PASSWORD
gh secret set KEY_ALIAS --body mmodel
gh secret set KEY_PASSWORD
```

Actions decodes the keystore to a temporary file, builds `assembleRelease` with those values, verifies the signature with `apksigner`, and publishes `app-release.apk`. Never commit the keystore or passwords.

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
design/            Source SVG for the adaptive launcher icon
.github/workflows/android.yml
```

## Notes / limitations

- Hugging Face's public API is used without a token. Heavy use can be rate-limited (HTTP 429); transient 5xx and rate-limit responses are retried with backoff.
- Explore loads 20 models at a time and fetches the next page near the end of the list. The leading cards on each page are enriched with full file sizes; any remaining card loads its exact sizes when opened.
- The **All** category can include non-GGUF models. Task categories add a GGUF filter so they stay download- and chat-friendly.
- Only published weight-file sizes are used. Repos without sizes show **Unknown**; those files can still be downloaded with a warning, and the app reads the size from the download response when available.
- Local chat is experimental and limited to single-file GGUFs on `arm64-v8a`.
- Gated/private models cannot be downloaded through the app; the link opens Hugging Face.
- Release APK signing requires the four repository secrets above; without them, CI publishes a debug-signed APK.
