# 0073 — Cases are the human's, and the stand comes first

[← decisions](README.md) · supersedes in part: [0072](0072-the-conversation-turn-is-the-machines.md)

## Status

Accepted 2026-10-24, by the owner.

## Context

0072 put the turn table in `flow/` and the model stand after the typed turn. A table in code is one no human
can read or extend, and a stand on a hosted repository is slow and costly; a local one is neither.

## Decision

- The turn table's rows are cases: markdown per role, `session-cases.md.dist`, `master-cases.md.dist` (read only
  when the Master is on), later `qa-cases.md.dist`, each copied and overridden like the briefs. A case names a
  brief step, never repeats its text. No case lives in code.
- Code keeps the engine and the frame: `FlowRules`, the guards, the read-only branches, and the table of which
  status gives the turn to whom (`Turns`). A case moves inside the frame only.
- Startup checks cases against code both ways: a case naming what code lacks, or a brief step no case sends,
  refuses to start, naming file and case. The table of who holds a turn is the frame's, so no check reads it.
- A reply no case takes is refused, written to `misses.jsonl` beside `state.json` (gitignored, rolled at 1 MB) and
  handed to the human, in `act` too. The Master never decides a miss nor adds a case; the human extends the cases.
- The stand comes first and is local: bare repositories as remotes, a Java code host and tracker served over MCP
  from files, a stand profile of its own; it runs by name and under `refresh`, never in `test`.

## Rejected

- Cases in `jagt.yml` or code: unreadable as a document, or closed to the human.
- The Master legitimising a case: the frame exists to hold it.
- A hosted stand repository: network, accounts and minutes per run for what a bare repository does.

## Reopen when

- A user's case needs a primitive the frame lacks more than rarely.
