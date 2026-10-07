# 0019 — Large work is cut by its spec, not split into requests

[← decisions](README.md) · rule: [`spec-changes`](../../openspec/specs/spec-changes/spec.md)

## Status

Accepted 2026-10-07.

## Context

- A large ticket lands as one request too big to review. A splitter would cut it into requests of 300–400
  changed lines, each targeting the previous one.
- It needs three designs nobody has: where a cut may fall (only a commit boundary stays buildable), how each
  cut targets the one before, and what happens to comments a later cut supersedes.
- The owner's reading of the trend: people stop reading code line by line. Agents write it, the Master reads it,
  and the human judges what the change does, which the spec states.
- No measurement shows an oversized request blocking a review.

## Decision

- jagt builds no request splitter.
- A large task is made reviewable through its spec: `openspec/changes/<task>/` states the behaviour, and the
  human reviews that. Effort goes into specs and the Master's reading, not into slicing diffs for human eyes.

## Rejected

- A splitter cutting at commit boundaries: three open designs, and it serves a reader the trend removes.
- Asking workers for smaller commits: a soft contract jagt cannot check.

## Reopen when

- A human reviewer refuses or stalls on a request for its size in 3+ tasks, measured in `finished.jsonl`.
- A code host or team rule caps request size and jagt's tasks hit it.
