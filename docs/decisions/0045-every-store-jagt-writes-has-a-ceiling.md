# 0045 — Every store jagt writes has a ceiling

[← decisions](README.md) · rule: [`guards`](../../openspec/specs/guards/spec.md)

## Status

Accepted 2026-10-15.

## Context

- A warning is not a bound: `finished.jsonl` and the kept documents only told the human past their limit.
- On disk 2026-10-15: headless reads' transcripts 1764 files, 497 MB, kept only by Claude's 30-day cleanup;
  `finished.jsonl` ~2 KB a task; `artifacts/` ~20 KB a task; `jagt-master.log` never emptied.
- `IntakeHistory`, `ProjectRouting` and `MasterRecord` read `finished.jsonl`: an intake must still see recent ids.

## Decision

- A headless read keeps no transcript (`--no-session-persistence`): nothing resumes it, its cost reaches
  `UsageLedger` from stdout.
- `finished.jsonl` past 10 MB packs its OLDER HALF beside it, gzip; the newer half stays, so every reader keeps
  the recent records. `finished` names the archives.
- `archive/` (one snapshot per `done`: documents and `finished.json`) past `archive.maxMegabytes` (20) deletes its
  oldest entries first; the newest stays. Decided 2026-10-28: the snapshots are for a human's last look, zips
  nobody opens bound nothing.
- An artifact past 10 MB is not copied; a `.not-kept` line names it.
- Each log rolls at 10 MB into one `.1`, replacing the last.
- A session's own transcripts are the agent CLI's: jagt reads them, its cleanup bounds them.

## Rejected

- Folding `finished.jsonl` like `UsageLedger`: its medians and the routing it teaches need whole records.
- Starting an empty `finished.jsonl`: an intake would reopen what was just finished.

## Reopen when

- A `.tmp` is found beside `state.json` or `usage-by-day.jsonl`: a failed move leaves it, and no test reaches that
  failure yet.
- The human never opens an archive: delete instead of packing.
- An agent CLI keeps transcripts with no cleanup of its own.
