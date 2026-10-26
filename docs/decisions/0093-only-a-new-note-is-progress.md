# 0093 — Only a new note is progress

[← decisions](README.md) · rule: [`unattended-work`](../../openspec/specs/unattended-work/spec.md),
[`review`](../../openspec/specs/review/spec.md) · amends [0070](0070-a-ship-counts-only-without-a-new-low.md)

## Status

Accepted 2026-10-26. Supersedes 0070's measure, the open-thread low, and the thread-count test of `stalledRounds`.

## Context

- A reviewer bot conceded to the agent's push-back; its note was the newest on the thread.
- Every sweep relayed the thread, the session found nothing to answer, the Master shipped nothing, three times.
- The dedup compared a digest of the brief, whose thread text the reading model copies and which differs per read.
- The open-thread count did not drop, so `stalledRounds` stopped the task and the human lifted it.

## Decision

- A review read carries each open thread's newest note id, as the host gives it.
- A round's note fingerprint is the sorted pairs of thread link and newest note id, built by `ReviewFacts`.
- A round with open threads, the same fingerprint as the last relayed and no red checks in either is not relayed.
- `stalledRounds` and `ships` count rounds whose fingerprint equals the one before; a new thread or note resets them.
- A round with no open thread keeps the digest dedup alone, so an approval is still news.
- Old rounds read the fingerprint as empty.

## Rejected

- Hashing the thread text more tolerantly: it stays a model's copy.
- Raising `stalledRounds`: the loop would only run longer.

## Reopen when

- A host reuses a note id for an edited note, and a changed note is not relayed.
- A task loops on new notes that carry nothing, and the ceilings never trip.
