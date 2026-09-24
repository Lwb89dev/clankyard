# Clankyard

Clankyard is a native Android coding workshop designed for phones and
tablets. It imports a project into a private workspace, lets you browse and
edit files, run local commands, and optionally use an AI assistant to propose
reviewable changes.

It is written in Kotlin with Jetpack Compose and Material 3. It is not a VS
Code clone, does not use a WebView, and is not Flutter. The mascot is available
in [`icon.png`](icon.png).

## Current status

Version `0.1.0`, pre-1.0. The app is usable as a local workshop, while some
integrations remain experimental. On-device Android build support exists in the
`:build:api`, `:build:runtime`, and `:build:engine` modules, but it is not yet
wired into the main application UI.

## Available features

- four-step onboarding: introduction, directory selection, Nostr login through
  Amber, and AI configuration;
- workspaces copied into app-private storage through the Storage Access
  Framework, with explicit project export;
- file manager with open, create, rename, delete, save-as, and refresh actions;
- sora-editor based editing for Kotlin, Java, Python, JavaScript/TypeScript,
  TSX/JSX, JSON, Markdown, Bash, C, and C++; YAML is opened as plain text;
- project search, file palette, and command palette;
- local `/system/bin/sh` shell confined to the selected workspace directory. It
  is not a Linux distribution, does not provide a PTY, and does not execute
  arbitrary user binaries;
- optional SSH execution with password authentication and host-key TOFU;
- local Git: init, status, diff, stage, unstage, and commit. Clone and push are
  not available yet;
- optional Clanker with ASK/PLAN/EDIT modes, filtered context, and patches that
  must be explicitly accepted or rejected. It never applies changes or commits
  automatically;
- OpenAI, Anthropic, xAI, Ollama, and OpenAI-compatible AI providers;
- Nostr login through Amber/NIP-55 or bunker NIP-46. The private `nsec` key
  never enters the app;
- five themes with coordinated accent colors and gradients: Rust, Terminal,
  Nostr, Firered, and Deepsea;
- adaptive layouts: in landscape, the workspace starts with the file manager,
  editor, and AI chat open as three panes;
- syntax highlighting for the supported programming languages, including
  separate token colors for keywords, functions, types, variables, parameters,
  strings, constants, comments, and operators.

## Privacy and security

Clankyard has no proprietary backend and does not collect API keys. Keys are
protected with Android Keystore; app backup is disabled for credentials,
drafts, journals, and workspaces. Secret filtering is applied before workspace
context is sent to AI providers.

AI is optional BYOK. A key stored on a rooted or compromised device can still
be used, so configure spending limits with the provider. See
[`SECURITY.md`](SECURITY.md) for the full policy.

## Build from source

Requirements: JDK 17 and Android SDK 36.

```bash
export ANDROID_HOME=/path/to/Android/Sdk
./gradlew :app:assembleDebug
./gradlew test :app:testDebugUnitTest :app:licenseCheck
```

Instrumented tests, using an Android device or emulator:

```bash
./gradlew :app:connectedDebugAndroidTest
```

The native execution experiments currently include the `arm64-v8a` ABI. The
on-device build modules are experimental and require separate runtime/toolchain
packs; they are not yet exposed as a main application feature.

## License

Original Clankyard code is released under the [MIT License](LICENSE).
Third-party dependencies retain their respective licenses; details and notices
are available in [`NOTICE`](NOTICE), [`LICENSES/`](LICENSES/), and
[`docs/licenses.md`](docs/licenses.md).
