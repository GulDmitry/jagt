# 0002 — The board is jagt's only UI

[← decisions](README.md) · rule: [`board`](../../openspec/specs/board/spec.md)

## Status

Accepted 2026-08-26 (`19ad0ec`), recorded retroactively 2026-10-02.

## Context

The console came first and its layout never held:

- 2026-07-29: Spring Shell 4 could not bind positional arguments (`44d1bda`); a hand-rolled JLine REPL replaced
  it the same day (`e52f95f`).
- 2026-07-30–31: a JLine status region pinned under a scrolling prompt took a dozen commits of gaps, an eaten
  header and a ghost dashboard on resize, until `d2f09b4` named the cause — DECSTBM resets on resize — and
  moved to a Lanterna full-screen TUI with a tmux PTY smoke test.
- 2026-08-13: the board became the default, behind an `OperatorUi` seam with web, tui and both (`f4031db`).
  The same day `20b3978` found `resume`, `prune`, `stats`, `help` and `quit` missing from the board, and made
  parity an invariant.

Parity held by discipline: every verb, the task projection, the grammar and the report text existed twice, and
the console was tested through two tmux smoke scripts nobody ran.

## Decision

- The browser board is jagt's one front-end over the core. It is not a gate in front of the sessions: a human
  still talks to any session directly in its own terminal. MCP is the agents' seam, not a UI.
- `19ad0ec` removed `MasterShell` (763 lines), `GrammarDispatch`, `OperatorUi`, `DashboardRenderer`, the
  `status` verb, `GlobalCommand.consoleOnly`, `ActionOrigin.CONSOLE`, Lanterna and `orchestrator.ui`: 86 files,
  3338 lines deleted. `BoardBanner` is what is left — one line saying where to look.
- A `jagt.yml` still carrying a removed key is named at startup, not silently ignored.

## Rejected

- Spring Shell 4: its argument binding did not attach to component-scanned commands (`44d1bda`).
- A JLine status region pinned under a scrolling prompt: it could not survive a resize (`d2f09b4`).
- Two surfaces held at parity (`20b3978`): a second surface is a second place for a capability to go missing.
- The ttyd terminal embedded in the board: shipped off by default and never turned on (`19ad0ec`).

## Reopen when

- A human needs jagt somewhere a browser on loopback cannot reach — and the new surface answers no question
  the board already answers.
