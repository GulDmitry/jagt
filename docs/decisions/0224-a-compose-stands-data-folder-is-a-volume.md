# 0224 — A compose stand's data folder is a volume on a fresh root name

[← decisions](README.md) · rule: [`testing`](../rules/testing.md) · relates:
[0130](0130-every-third-party-is-a-configured-seam.md)

## Status

Accepted 2026-11-22, after the cause was measured.

## Context

- About six compose stands starting at once: `Files.createDirectories(<root>/jagt/opencode-gate)` threw
  `NoSuchFileException` inside the container.
- An in-JVM probe at the throw: `stat` of `<root>/jagt` said it is a directory, while `Files.list(<root>)` and the
  host both lacked it, still 2 s later.
- So the engine's view of the host bind mount went stale under host file-system load (measured engine: OrbStack;
  CI on Linux stayed green).
- A probe on the base image, 8 containers under host load: 2 of 56 starts read a truncated or missing class file
  through a read-only bind mount.
- Every failing write was under `jagt/`.
- A volume nested under the bind mount, on a root name an earlier run had used, showed the bare mountpoint:
  `EACCES` on the same write. On fresh names it showed the volume every time.

## Decision

- `<root>/jagt`, jagt's data folder, is a named volume beneath the bind-mounted root; the bind mount keeps the
  fixtures and every file the host reads.
- A one-shot container prepares the volume (`chmod 0777`) before `up`, so the run's uid can write it.
- The host never reads that folder in place: `JagtContainer.state()` and `harvest()` copy `jagt/state.json` out
  with `compose cp`; `close()`, `StandRun.record()` and `StandRun.violations()` harvest first.
- `sharesRoot()` stays true: the full scenarios read `origin.git`, the logs and the records on the host.
- A root name is the epoch millisecond of its start, never an index from 1, so a deleted folder's name is not
  taken again.
- No retry, no relaunch, no cut of parallelism.

## Rejected

- The whole root on a volume, as `KindEngine` does: the full scenarios would abort on compose, as on kind.
- Declaring the engine unsupported: the stand then runs on Linux alone.
- A retry or a lower `standParallelism`: hides a stale view other reads would still meet.

## Reopen when

- A stale-view failure is seen on another bind-mounted path (the root, `home`, the secret).
- The nested volume shows its mountpoint again on a fresh name.
- The engine fixes its bind-mount coherence and a probe under load stays clean.
