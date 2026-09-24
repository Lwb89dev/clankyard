# Architecture

Single `MainActivity`, Jetpack Compose, Navigation 2, Hilt+KSP, UDF.

## Modules

| Path | Role |
| --- | --- |
| `:app` | Shell, DI, `LlmProviderFactory`, AdaptiveShell |
| `:core:model` | Paths, credentials, ids |
| `:core:common` | Redacting logger |
| `:core:ui` | Theme, DataStore chrome, semantics, mascot still |
| `:core:security` | Keystore AES-256-GCM store |
| `:workspace` | File-backed workshop + WAL journal (JVM) |
| `:editor` | sora-editor wrapper + TextMate grammars |
| `:search` | In-process text search |
| `:diff` | Myers diff |
| `:git` | JGit, no clone/push |
| `:terminal:api` | `ExecutionBackend`, `SwitchingExecutionBackend` |
| `:terminal:local` | `/system/bin/sh` via `ProcessBuilder` |
| `:terminal:ssh` | Apache MINA SSHD client, password + host-key TOFU |
| `:build:api` | File-free `BuildService` types (on-device BUILD) |
| `:build:runtime` | Runtime packs, `LinkerExec` (no jniLibs) |
| `:ai:provider-api` | `LlmProvider`, Completions/Messages HTTP |
| `:ai:providers:*` | OpenAI, Anthropic, xAI, compatible, Fake |
| `:ai:secret` | `SecretFilter` |
| `:ai:context` | ASK/PLAN/EDIT context pack |
| `:ai:tools` | SAFE_READ + REVIEW_REQUIRED only |
| `:ai:patch` | Replace-only `PatchEngine`, `ApplyPatchUseCase` |
| `:ai:agent` | `DefaultAgentOrchestrator` (JVM; credential on the request) |
| `:feature:*` | Explorer, search, git, diff, terminal, settings, clanker, picker |

## Workshop files

`WorkshopEnvironment` creates `filesDir/environment/` on first launch.
Workshops live under `environment/workspaces/` (legacy `filesDir/workspaces/`
is renamed once). SAF copy-in is a copy, not a live tree. Export is mandatory.
Journals, drafts, and Keystore credentials stay **outside** `environment/`.
Public FUSE is not the Git root (`core.filemode=false`).

On-device BUILD runtimes, caches, tmp, and last APKs are siblings of
`workspaces/` under `environment/` (`runtimes/`, `cache/`, `tmp/`,
`artifacts/`). They are not workshop source. See `docs/build-on-device.md`.

## The Clanker

1. Settings persist provider/model/base URL in `SharedPreferences`.
2. Keys go to `SecureCredentialStore` slots `llm.<id>.default` or
   `llm.openai-compatible.<host>`.
3. `ClankerViewModel` resolves the slot, builds a provider, and runs
   `DefaultAgentOrchestrator`.
4. EDIT proposes a `PatchSet`. Accept calls `ApplyPatchUseCase`. Reject
   drops it. The orchestrator never writes and never commits.

Transcript is ephemeral (process death drops it). Tabs restore from DataStore.

## Terminal

`LocalProcessBackend` is a **one-shot** `/system/bin/sh -c` (or `/bin/sh` on
the JVM), cwd jailed under `environment/`, no PTY, no `-i` (Android mkshrc
`bind` spam). Banner: sandbox, cwd reset each line, not a Linux distro, not a
chroot. `canExecUserBinaries` stays false. Downloaded JDK/aapt2 are **not**
on this PATH.

`SshExecutionBackend` (Settings → Execution → Remote SSH) is interactive
password + host-key TOFU. Slot `ssh.<host>`. SSH is not the on-device BUILD
implementation.

## Chrome

Expanded: files | editor | Clanker, plus bottom tabs Terminal / Problems /
Git / Output (Problems and Output are BUILD surfaces). Compact: five
destinations (Editor, Files, Clanker, Terminal, Git); Build log is a Dialog,
not a sixth destination. Editor session is Activity-retained.


