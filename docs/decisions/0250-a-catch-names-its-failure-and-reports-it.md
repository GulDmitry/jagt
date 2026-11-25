# 0250 — A catch names its failure and reports it

[← decisions](README.md) · rule: [`seams`](../rules/seams.md#a-read-that-failed-is-never-an-answer) ·
kind: [`ARCHITECTURE.md`](../../ARCHITECTURE.md#the-kinds) ·
on [0176](0176-every-kind-has-a-ceiling.md), [0180](0180-each-spheres-industry-standard-is-jagts-floor.md)

## Status

Accepted 2026-11-25.

## Context

- An audit found catches that turned a failed read into an answer: an unreadable `.git` pointer counted an orphan,
  a transient stamp read raised a rebuilt banner, a null in a role lint read as a lint line.
- `catch (RuntimeException)` also swallows a programming error (a null, a bad cast) that should fail loudly.
- 154 catch lines naming a broad type, multi-catch included, stood in main code (121 of them single-type);
  205 catches (all kinds) neither log nor rethrow.

## Decision

- **A catch names the typed failure it handles**; a read that failed is unreadable, never absent or false.
- **A catch logs, with `cause`, or rethrows**; one answering a vanished file (`NoSuchFileException`) may stay quiet.
- **Row `broad-catches`** (scan, `set: main`, `per: line`) counts a catch line naming `RuntimeException`, `Exception` or `Throwable`,
  a multi-catch once, max 147, only falling.
- **Row `silent-catches`** (`SilentCatchFitness`, all main code) counts a catch with neither a `log.` call nor a `throw`, max 205, only falling.
- Fixed with the rows: orphan pointer, jar stamp, `Roles#lint`, `HumanPaths`, `RepoDeparture`, `RemoteCheck`,
  `CodexRollouts`, `HttpEnvironmentProbe`, `FailurePage`, `LogSpend`.

## Rejected

- Max 0 now: the other catches need their own cause read, not a blind change.
- A regex for the silent catch: a catch body spans lines, so the check reads source.

## Reopen when

- A catch passes by another spelling (a delegate that logs elsewhere): the row reads syntax, not intent.
