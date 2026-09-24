# Contributing

Patches should keep the workshop small, honest, and buildable without AI.

## Ground rules

- Apache-2.0 for original code. Do not vendor GPL. License CI fails if
  `com.termux:termux-shared` or `termux-app` appears on the app classpath.
- Consume sora-editor as **unmodified Maven AARs**. Do not copy its sources
  into this tree. Relink steps: [docs/licenses.md](docs/licenses.md).
- Do not copy VS Code or Monaco sources. Syntax grammars must have a clear
  MIT/Apache/BSD SPDX line in `NOTICE`, otherwise open as plain text.
- Never auto-commit Clanker edits. There is no settings toggle for this.
- HIGH_RISK tools stay unregistered. Agent writes go through `validateAndDiff`
  and `ApplyPatchUseCase` after an explicit Accept.
- `:workspace` stays JVM-pure. SAF `Uri` belongs in `:feature:workspace-picker`.
- LLM adapters take a `Credential` per call. They must not depend on
  `:core:security`.
- Empty-AI remains first-class: open, edit, search, Git commit, sandbox shell
  with no key configured.

## Build

JDK 17, Android SDK 36. See the README. Run `./gradlew :app:licenseCheck`
before sending a change that touches Gradle coordinates.

## Style

Kotlin, three levels of indentation is a smell. Match neighboring files.
Keep comments short and factual.
