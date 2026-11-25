# 0257 — herdr is a session host beside tmux

[← decisions](README.md) · rule: [`seams`](../rules/seams.md) · spec: [`runtime`](../../openspec/specs/runtime/spec.md) ·
on [0130](0130-every-third-party-is-a-configured-seam.md), [0180](0180-each-spheres-industry-standard-is-jagts-floor.md)

## Status

Accepted 2026-11-25.

## Context

- herdr 0.9.5 (Apache-2.0) is a terminal multiplexer with persistent named sessions and a documented NDJSON socket API, on macOS and Linux.
- `SessionHost` had one implementation; every window guarantee of the runtime spec was written against tmux.
- Observed on 0.9.5, `herdr api schema --json` protocol 22:
  - a tab takes a label, a working directory and an environment, but no command; labels need not be unique;
  - a tab closes when its process exits, and closing it ends its processes;
  - `pane.process_info` lists the foreground processes beside the shell's pid;
  - an absent server leaves no socket and answers `server_not_running`;
  - after a server restart tab and pane ids stay, each pane holds a fresh shell;
  - `pane.read` keeps colour in `ansi` format; a pane's `revision` does not move with output, and no activity time exists.

## Decision

- **`orchestrator.session-host`** is `tmux` (default) or `herdr`; the other host's beans (`SessionHost`, `WindowStream`,
  `ReadScope`, its startup check) do not exist. The count of startup checks rises from 34 to 36 (`TmuxCheck`, `HerdrCheck`),
  that of spec capabilities from 55 to 56 (`session-host`: the runtime spec is at its 200 lines).
- **A window is a tab labelled with its name**, in one workspace labelled with the session; the same name is replaced, never doubled.
- **The agent line is typed into the tab's shell, which it replaces** (`exec sh -c '<line>; <tail>'`):
  the tab closes after the tail, so no shell is left, as in tmux.
- **The agent runs while a foreground process other than the pane's own exists**, or while the typed line is on screen unrun and unexited.
- **No socket or a refused connection is no window; any other failed read is unknown** (`WindowsUnreadable`), as with tmux.
- **Activity is the time jagt last saw the screen differ**, read from `pane.read`; a screen met first counts from then.
- **The server starts detached** (`herdr --session <name> server`) where none answers; a viewer attaches with `herdr session attach`.
- **`WindowStream` redraws the screen on change**: herdr has no output stream and sizes a window by its own client, so a view does not resize it.
- **`orchestrator.herdr.command` and `home`** (`HerdrProperties`, in `adapter/herdr`) name the binary and the folder holding
  `sessions/<name>/herdr.sock`.
- **The socket is behind `HerdrWire`**: unit tests drive a fake wire that refuses any request the real schema
  (`adapter/src/test/resources/herdr-request-schema.json`) does not allow, since a test owns no thread to serve a socket;
  `HerdrSessionHostE2eTest` drives real herdr in a named session of its own.

## Rejected

- herdr's own agent status as jagt's attention signal: jagt's hooks already report it; a second source would disagree.
- `agent.start`: it launches herdr's agent kinds, not jagt's launch line and environment.
- Raising a pane's `revision` as activity: it did not move with output.

## Reopen when

- herdr's tab creation takes a command, or its API streams pane output.
- A herdr release changes the schema `herdr-request-schema.json` records.
