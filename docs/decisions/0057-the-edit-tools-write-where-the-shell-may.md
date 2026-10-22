# 0057 — The edit tools write where the shell may

[← decisions](README.md) · spec: [`runtime`](../../openspec/specs/runtime/spec.md)

## Status

Accepted 2026-10-22.

## Context

- A session's sandbox binds its shell alone; Claude Code's edit tools ask outside the working directory unless a rule names the path.
- A directory the shell could write, such as the specs repository, still asked before an edit.

## Decision

- Every path in a session's sandbox `writable` list also gets an `Edit(//<abs>/**)` allow rule (`ClaudeAgentRuntime.editRules`).
- One list answers where a session writes; the rules derive from it.

## Evidence

- Probe, 2026-10-22, `claude -p` in default mode:

| setting | Write outside the working directory |
|---------|-------------------------------------|
| sandbox `allowWrite` alone | denied |
| `permissions.additionalDirectories` alone | denied |
| `Edit(//<dir>/**)` | written |

## Rejected

- `additionalDirectories`: it did not admit the write.
- Granting only the human-configured `allowWrite`: two sources of truth for where a session writes.

## Reopen when

- Claude Code's sandbox starts binding the edit tools.
- A writable path must stay shell-only.
