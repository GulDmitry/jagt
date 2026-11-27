# 0267 — The key choosing a tool names its kind

[← decisions](README.md) · rule: [`seams`](../rules/seams.md) · spec: [`session-host`](../../openspec/specs/session-host/spec.md) ·
on [0130](0130-every-third-party-is-a-configured-seam.md), [0257](0257-herdr-is-a-session-host-beside-tmux.md)

## Status

Accepted 2026-11-27.

## Context

- 0257 put herdr beside tmux, yet the key choosing the tool was `sessionHost`, the tmux settings sat at the top level
  as `tmuxCommand` and `tmuxSocket`, and the session name was `viewer.tmuxSession`, handed to whichever tool ran.
- `EdgeNamesFitness` exempted those three names as "vendor-named keys by design".

## Decision

- **`orchestrator.multiplexer`** is `tmux` (default) or `herdr`: a vendor's name is only the value of the key choosing it.
- **`viewer.session`** names the session either tool runs.
- **Each tool's settings sit in its own block**: `tmux: {command, socket}` (`TmuxProperties`), `herdr: {command, home}`.
- **The old keys read as aliases** — `sessionHost`, `tmuxCommand`, `tmuxSocket`, `viewer.tmuxSession` — until release 0.32.0
  (`RenamedKeys.DROPPED_IN`); the new key wins where both are written.
  `RenamedKeysAlias` aliases them in Spring's environment, `ConfigService` in the file's own read.
  `ConfigCheck` notes each old key written with its new key and that release; startup logs each note once as WARN.
- **`config-key-vendors`** (`ConfigKeyVendorsFitness`) fails on a vendor's name in a config key outside that vendor's own block.
  It reads the generated schema of `jagt.yml`, so a key in a record, a default or a `@ConditionalOnProperty` is held alike.
- `EdgeNamesFitness` exempts only `RenamedKeys`, the table holding the retired names.
- `kittyCommand` and `kittyFontSize` stay, listed in the check as pending their own rename into a `kitty` block.

## Rejected

- Renaming the `SessionHost` port in code: the port names a role, not a vendor.
- Warning from the environment's post-processor: logging is not set up yet when it runs.
- Dropping the old keys at once: a `jagt.yml` written for 0257 would stop selecting its tool in silence.

## Reopen when

- Release 0.32.0 is cut: `RenamedKeys`, `RenamedKeysAlias` and the notes of `ConfigCheck` go, and the old keys become retired keys.
- A second key names a vendor outside its block: the check names it.
