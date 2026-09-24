# Clankyard

A tablet-first native Android workshop for code. Kotlin, Jetpack Compose,
Material 3. Not a VS Code clone, not a WebView, not Flutter.

The mascot lives at [`icon.png`](icon.png). Do not redraw it.

The IDE is useful with **no AI key**. The Clanker (optional BYOK agent) is a
welder you invite in, not the building.

## Status

Pre-1.0. F-Droid first (`applicationId` `dev.clankyard.app`, `minSdk` 29).
Apache-2.0, except unmodified sora-editor AARs (LGPL-2.1-or-later).

## What it does today

- File-backed workshops under app storage, with SAF copy-in and mandatory export
- sora-editor for Kotlin, Java, Python, JS/TS, JSON, Markdown, Bash, C/C++
  (YAML opens as plain text)
- Explorer, project search, drafts, local Git (init/status/diff/stage/commit —
  no clone/push yet)
- Sandbox shell (`/system/bin/sh`). This is **not** a Linux distro. Optional
  remote SSH (password + host-key TOFU) from Settings
- The Clanker: ASK / PLAN / EDIT with per-file patch review. Never auto-commits
- Settings: orange gear next to Save. API keys, optional Amber/NIP-55 login, SSH

## Build

JDK 17. Android SDK with `compileSdk` 36.

```bash
export ANDROID_HOME=/path/to/Android/Sdk
./gradlew :app:assembleDebug
./gradlew test testDebugUnitTest :app:licenseCheck
```

Instrumented tests (device or emulator, API 29 and 36):

```bash
./gradlew :app:connectedDebugAndroidTest
```

## Bring your own key

Settings stores API keys in Android Keystore (AES-256-GCM). Keys never leave
the device through a Clankyard backend — there is no Clankyard backend.

Supported providers: OpenAI Completions, Anthropic Messages, xAI Completions,
HTTPS OpenAI-compatible hosts. `http://` is rejected; local Ollama is post-MVP.

A compromised or rooted device can still spend a key. First save requires an
acknowledgement checkbox. Reveal uses `FLAG_SECURE`.

## License

- Clankyard source: [Apache License 2.0](LICENSE)
- Third-party notices: [NOTICE](NOTICE)
- How to relink sora-editor: [docs/licenses.md](docs/licenses.md)
- Architecture sketch: [docs/architecture.md](docs/architecture.md)
- Security contacts: [SECURITY.md](SECURITY.md)
