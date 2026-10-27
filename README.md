# Mmodel

**Mmodel = mobile model.** Find AI models your Android phone can actually run.

Mmodel reads your phone's real specifications, searches the public [Hugging Face](https://huggingface.co) catalog, and labels each model with an estimated fit:

- **Works** — comfortable inside your safe RAM budget
- **Slow** — likely to load, but tight on memory
- **Won't run** — too large for this phone (or not enough free storage)
- **Unknown** — the repo does not publish file sizes

It also recommends popular GGUF models that fit your device, and shows the numbers behind every verdict.

## Features

- **Live device profile** — RAM, free storage, CPU cores, ABI, chipset, OpenGL ES version, Android version
- **Hugging Face search** — 50 results per request, sort by trending / likes / recently updated, GGUF filter
- **Smart fit estimate** — smallest GGUF quantization (shards summed), RAM overhead, storage check
- **Recommended for you** — top GGUF picks scored by popularity and fit
- **Model details** — parameters, context length, quant options, downloads, likes, tags, and a direct Hugging Face link
- **Material 3 UI** — custom modern corner shapes, letter avatars, gradients, light and dark themes
- No accounts, no analytics, no tracking. Only the `INTERNET` permission.

## Install the APK

Every push to `main` builds a debug APK in GitHub Actions.

**Direct download (latest):**
[app-debug.apk](https://github.com/soumik183/Mmodel/releases/download/latest/app-debug.apk)

Or open the [latest release page](https://github.com/soumik183/Mmodel/releases/tag/latest), or download the `Mmodel-debug-apk` artifact from the [Actions tab](https://github.com/soumik183/Mmodel/actions).

> Android may warn about installing an unknown app because the APK is debug-signed. Allow install from your browser/files app. It is not a Play Store build.

## How the fit estimate works

1. Read the **smallest published GGUF file** in the repo. For sharded GGUFs, all shards of a quantization are summed. For non-GGUF repos, safetensors / PyTorch / ONNX weight sizes are summed.
2. Estimate runtime RAM: `file size × 1.15 + 600 MB` (GGUF) or `file size × 1.2 + 900 MB` (other formats), to leave room for the KV cache and runtime.
3. Compare with a safe budget: `min(total RAM − 1.5 GB, 75% of total RAM)`.
   - `≤ 65%` of budget → **Works**
   - `≤ 100%` → **Slow**
   - `> 100%` → **Won't run**
4. If the download plus 300 MB does not fit in free storage → **Won't run**.

Quantization, context length, background apps, thermals and the runtime (llama.cpp, MLC, etc.) all change real performance. **This is a guide, not a benchmark.**

## Build

The project standardizes on:

- Kotlin 2.0.21, AGP 8.7.3, Gradle 8.9, JDK 17
- compileSdk / targetSdk 35, minSdk 26
- Jetpack Compose Material 3 (BOM 2024.12.01)

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
  data/           Hugging Face models + API, device specs, fit logic
  ui/             ViewModel, navigation, formatting
  ui/components/  Letter avatars, badges, cards, rows
  ui/screens/     Home, Explore, Model detail
  ui/theme/       Material 3 theme and shapes
app/src/test/     JVM tests for the fit logic
.github/workflows/android.yml
```

## Notes / limitations

- Hugging Face's public API is used without a token. Heavy use can be rate-limited (HTTP 429).
- The Explore screen loads the top 50 matches per query; search reaches the full catalog.
- Only GGUF and other weight-file sizes are used. Repos without published sizes show **Unknown**.
- Gated/private models cannot be downloaded through the app; the link opens Hugging Face.
- Debug APK only. A release build would need signing.
