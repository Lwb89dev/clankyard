# Security

Clankyard has no proprietary backend and does not collect API keys.

## Keys

BYOK secrets live in Android Keystore (`clankyard.credentials.v1`) with
ciphertext under `filesDir/credentials/`. Cloud backup and device-to-device
transfer exclude `credentials/`, `drafts/`, `journal/`, and `workspaces/`.
`allowBackup` is false.

A rooted or compromised device can still use a stored key. Treat this as
advanced BYOK, not a server-side secret. Set spend caps at the provider.

Do not paste keys into logs, issues, or backups. Provider HTTP clients do
not attach body log interceptors.

Clankyard never uploads credentials. There is no account server.

## Nostr (Amber / bunker)

Login uses NIP-55 (Amber on this device) or a stored `bunker://` URI (NIP-46).
**The nsec never enters Clankyard.** Amber will not export it; pasting `nsec1…`
is rejected. Optional wrap asks Amber to NIP-44-encrypt API keys so the
filesDir blob is ciphertext Amber must decrypt. Android Keystore still wraps
that blob.

Email/password for ChatGPT or Claude.ai is not collected and is not a valid
provider login here. Use dashboard API keys.

## Agent

The Clanker cannot apply patches without an explicit Accept. It never
auto-commits. HIGH_RISK tools (shell write, git push, delete) are not
registered. Outbound context passes through `SecretFilter`.

## Reporting

Open a GitHub issue with the `security` label, or email the maintainer listed
on the F-Droid page once published. Do not attach live keys.
