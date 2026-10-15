# 0038 — A panel's first role writes the round

[← decisions](README.md) · rule: [`master`](../../openspec/specs/master/spec.md)

## Status

Accepted 2026-10-13.

## Context

Probed 2026-10-13 with two `claude -p` runs a few seconds apart, the second differing only in the role's line:

| round placed | second run read | second run wrote |
|---|---|---|
| in the prompt, before the role's line | 6.3k | 38.1k |
| in `--append-system-prompt`, the role's line the prompt | 52.1k | 1.8k |

The cache matches a prefix only up to a block boundary. Replayed on 648 real role runs, 135 panels of 11 tasks
(2026-09-30..10-07, transcripts from before reads stopped persisting): of 91.1M input-equivalent the Master paid
for cache, the 5m TTL saves 22.7M and the stagger 20.1M more; the first role ran a median 0.5 minutes, past 5 in 6.
Roles started together all write before any can read.
The Master's reads ran without `FORCE_PROMPT_CACHING_5M`, so at the 1h TTL's 2× write.

## Decision

- `MasterPanel.review` sends the round in `Round.shared`, after the brief; `Round.prompt` is the role's line.
- The first role reads alone; the rest start together once it answered, within its 5-minute window.
- `HeadlessClaude.run` prefixes every headless command with `FORCE_PROMPT_CACHING_5M=1`, the Master's reads included.

## Rejected

- Starting the rest on the first role's first API response: `--output-format json` reports nothing until the end.
- Keeping the briefs cached across rounds: 21 of 739 runs read past the CLI's own prefix (0037).

## Reopen when

- `usage-by-day.jsonl` shows `MASTER_REVIEW` cost per call not falling against the replay.
- `stats` shows a panel's later roles starting COLD: the first role ran past the 5-minute window.
- The panel's added latency, one role's run, delays the human's verdict more than the saving is worth.
