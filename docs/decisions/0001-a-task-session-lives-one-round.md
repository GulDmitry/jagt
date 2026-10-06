# 0001 — A task session lives one round

[← decisions](README.md) · rule: [`runtime`](../../openspec/specs/runtime/spec.md)

## Status

Accepted 2026-10-02 (`e1401a2`).

## Context

Two tasks' session logs, measured on 2026-10-01:

| task | turns | mean context per turn | cache read | cache write | output |
|------|-------|-----------------------|------------|-------------|--------|
| A | 1250 | 575k | 75% | 21% | 4% |
| B | 1533 | 242k | 65% | 28% | 7% |

The money is context, not prose: every turn rereads the whole history. Task A woke 11 times after 1–92 idle
hours, rewriting 350–900k tokens each time, because `--continue` resumed one conversation for weeks. Text the
model wrote was 1.5–5% of context; tool results were the rest (browser snapshots 56% in B, grep and cat 46% in A).

## Decision

- New instructions to a session idle past the CLI's cache lifetime (Claude: 1h) start a fresh session.
- The fresh session starts from `task_notes.md`, which the session rewrites at every REVIEW_PENDING; jagt refuses
  a hand-back whose notes are missing, older than the round, or past 16,000 characters (~4k tokens: twice the
  1–2k Anthropic gives a condensed hand-off, for tasks that run for months).
- Sessions compact at 300k tokens, set on the launch command so every worktree gets it.

## Rejected

- Shorter terminal prose: at most 2% of the cost.
- A longer cache TTL: no TTL survives a review that waits hours or days.
- A light model summarising the transcript: it rereads 500k; the session writes the notes with its cache warm.
- Claude Code's own auto-memory: neither jagt nor the human sees it, and the model decides when it writes.

## Reopen when

- A fresh session's first round costs more than the continued conversation it replaced.
- Fresh sessions redo work the notes should have carried — the cap or the notes' shape is wrong.

Sources: [costs](https://code.claude.com/docs/en/costs), [prompt caching](https://code.claude.com/docs/en/prompt-caching),
[context engineering](https://www.anthropic.com/engineering/effective-context-engineering-for-ai-agents).
