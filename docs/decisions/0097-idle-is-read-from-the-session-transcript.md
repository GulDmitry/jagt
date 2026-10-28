# 0097 — Idle is read from the session transcript

[← decisions](README.md) · rule: [`cold-prompts`](../../openspec/specs/cold-prompts/spec.md)

Supersedes in part [0094](0094-a-prompt-typed-into-a-cold-session-goes-to-a-fresh-one.md).

## Status

Accepted 2026-10-27.

## Context

0094 counted idle from a mark jagt kept in memory, set by the Stop hook. A restart wipes it, and an unlogged event
cleared it on 2026-10-27: the turn ended 13:16Z, a prompt came at 18:06Z, and the gate judged the session warm.

## Decision

- Supersedes 0094's bullet "Idle is counted from the turn's end jagt recorded, not the transcript".
- Idle counts from the session's own transcript: the last `stop_hook_summary` or `turn_duration` entry.
- A turn is open when an assistant entry is newer than the last turn end: no idle, no cold prompt.
- Only assistant and turn-end entries are read. The new prompt in the log is no objection, and an idle compaction
  (`compact_boundary` plus its summary) does not reset idle.
- The turn end is read from the log the prompting session names, never the newest in the directory:
  a fresh session has no log yet and is not cold.
- `SessionProbe` keeps no prompt mark.

## Rejected

- Persisting the mark in `state.json`: a second copy of what the transcript already holds, and still lost to an unlogged clear.

## Reopen when

- Claude stops writing a turn-end entry.
