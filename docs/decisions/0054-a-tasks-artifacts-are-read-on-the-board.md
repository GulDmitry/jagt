# 0054 — A task's artifacts are read on the board

[← decisions](README.md) · spec: [`board-workspace`](../../openspec/specs/board-workspace/spec.md)
## Status

Accepted 2026-10-21.

## Context

- A session's `plan.md`, `task_notes.md` and sketch could be read only by opening the worktree in an editor or terminal.
- The board has no build step and no external asset; an agent's text must not run script on it.

## Decision

- jagt draws and stores nothing: the session writes `task_visual.md`, the board reads the `Artifact` files present, on request.
- Markdown renders server-side with commonmark-java and its tables extension: raw HTML escaped, URLs sanitised.
- Mermaid is vendored as the single-file `mermaid.min.js` beside its licence, loaded only when a document holds a fence,
  `securityLevel: 'strict'`, `startOnLoad: false`.
- Both libraries are the newest release at least 14 days old: commonmark 0.30.0, mermaid 12.1.0.
- A name is looked up in `Artifact`, never joined to a path; unknown task, unknown name, absent and unreadable are four answers
  ([0008](0008-a-failed-read-is-never-not-found.md)).

## Rejected

- A client markdown library plus a sanitizer: two libraries and a sanitizer config stand between an agent's text and `innerHTML`.
- Opening the file in the editor only: the human leaves the board to read a plan.
- Rendering the pictures in jagt: a second drawing path to keep beside the agent's own, for no fact jagt owns.

## Reopen when

- A mermaid release changes `securityLevel: 'strict'` or the single-file build.
- A document the board must show is not markdown.
