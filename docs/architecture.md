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
| `:ai:provider-api` | `LlmProvider`, Completions/Messages HTTP |
| `:ai:providers:*` | OpenAI, Anthropic, xAI, compatible, Fake |
| `:ai:secret` | `SecretFilter` |
| `:ai:context` | ASK/PLAN/EDIT context pack |
| `:ai:tools` | SAFE_READ + REVIEW_REQUIRED only |
| `:ai:patch` | Replace-only `PatchEngine`, `ApplyPatchUseCase` |
| `:ai:agent` | `DefaultAgentOrchestrator` (JVM; credential on the request) |
| `:feature:*` | Explorer, search, git, diff, terminal, settings, clanker, picker |

## Workshop files

Workshops live under `filesDir/workspaces/`. SAF copy-in is a copy, not a
live tree. Export (zip or tree write-back) is mandatory. Public FUSE is not
the Git root — JGit needs `O_EXCL`-safe app storage (`core.filemode=false`).

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

`LocalProcessBackend` starts `/system/bin/sh -i` (or `/bin/sh` on the JVM)
with cwd = workshop root, stderr merged, no PTY. Banner: “This is a sandbox
shell, not a Linux distro.”

`SshExecutionBackend` (Settings → Execution → Remote SSH) opens a password
session with Apache MINA SSHD. Unknown host keys are rejected and shown as
`SHA256:…` until the user taps Trust. Slot `ssh.<host>` holds the password.
Termux interop and on-device Android toolchains remain later work.

## Chrome

Expanded: files | editor | Clanker, plus a bottom tools strip (terminal / git).
Compact: destinations, editor session Activity-retained so opening Clanker
does not destroy dirty buffers.
