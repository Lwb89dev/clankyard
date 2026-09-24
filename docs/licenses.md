# Licenses and relink

Clankyard is MIT. Third-party notices live in [`NOTICE`](../NOTICE).
Full texts that are not MIT are under [`LICENSES/`](../LICENSES/).

## sora-editor (LGPL-2.1-or-later)

Clankyard consumes unmodified Maven AARs:

- `io.github.rosemoe:editor`
- `io.github.rosemoe:language-java`
- `io.github.rosemoe:language-textmate`

pinned by `io.github.rosemoe:editor-bom` (see `gradle/libs.versions.toml`).
Sources are **not** vendored.

### Relink with a modified editor AAR

1. Build your fork of [sora-editor](https://github.com/Rosemoe/sora-editor)
   so the Maven coordinates stay `io.github.rosemoe:editor` (and the language
   modules you need) at the same version Clankyard pins, or publish them to a
   local Maven repo.
2. Point Gradle at that repo (`mavenLocal()`, or a `maven { url = ... }`
   block in `settings.gradle.kts`).
3. `./gradlew :app:assembleDebug` (or Release). Corresponding source for
   Clankyard is this repository.

You may also overlay the AAR files in a composite build. Do not copy
sora-editor Java sources into `editor/`.

## JGit

`org.eclipse.jgit:org.eclipse.jgit` — Eclipse Distribution License v1.0
(BSD-3-Clause). See `NOTICE`.

## Termux

The sandbox shell is `ProcessBuilder` + `/system/bin/sh`. We do **not**
depend on `termux-app` or `termux-shared` (GPL islands). CI fails if those
coordinates appear. Optional Apache-2.0 `terminal-emulator` / `terminal-view`
vendor is post-MVP.

## Apache MINA SSHD

`org.apache.sshd:sshd-core` (Apache-2.0) is the remote `ExecutionBackend`.
It is not GPL. Termux-app / termux-shared stay forbidden.

## OkHttp and other Apache-2.0 / MIT libraries

OkHttp, Kotlin, AndroidX, Hilt, protobuf, and similar dependencies are used
under their upstream Apache-2.0 or MIT licenses. Pins are in
`gradle/libs.versions.toml`.
