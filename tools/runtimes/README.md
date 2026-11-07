# clankyard-runtimes (placeholder)

This folder is the in-tree scaffold for the **sibling** tools-repo that must
publish relocatable Android ARM64 zips **before** Clankyard can download a JDK
or Bionic aapt2.

Packs are **not published from this tree**. `RuntimeManifest` entries stay
unpublished (`url` and `sha256` empty) until a public HTTPS zip + SHA-256 exist.

## Expected artifacts

| Pack | License | Notes |
| --- | --- | --- |
| OpenJDK 17 ARM64 | GPL-2.0-with-classpath-exception | API 29 sysroot, `$ORIGIN` RPATH, zip only |
| Bionic aapt2 | Apache-2.0 | API 29, zip only |
| AOSP android.jar stub API 34 | Apache-2.0 | default compile jar |

Gradle 8.11.x is the official Apache-2.0 `-bin.zip` from `services.gradle.org`
and is listed only after the JDK pack exists.

## Checkpoint

If this README's date is reached with no zip, Clankyard keeps the honest
"Gradle not supported on this device" path (POC-9 shrink is an explicit
decision, not a silent CodeAssist pivot).

Scripts here must not copy Termux `$PREFIX` or `termux-app` / `termux-shared`.
