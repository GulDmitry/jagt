# 0068 — A session asks for the repository it lacks

[← decisions](README.md) · rule: [`git`](../../openspec/specs/git/spec.md), [`master`](../../openspec/specs/master/spec.md)
· supersedes in part: [0066](0066-only-the-human-opens-a-task.md)

## Status

Accepted 2026-10-23.

## Context

A task's repositories are fixed when it opens. A task in one project that needed another configured project's
repository sent the human a question, and the human had no verb: [0066](0066-only-the-human-opens-a-task.md)
sent "a repository the task lacks" to them and nothing could attach one.

## Decision

- The session asks with `update_agent_status`, `outcome=question` and the typed `needsProject`, a key `list_projects` prints.
- jagt validates the key against the configured projects and records it on the task until granted or superseded.
- `add-repo` is a per-task verb, offered only on that question; it cuts the worktree as `do` does and tells the session.
- In `act` the Master presses it, gated by the right `repo`; `mine: [repo]` keeps it yours.
- The Master never answers that question in prose: a repository is attached by jagt, not decided by a model.
- A task still opens only from a deterministic source; one more repository is no second task.

## Rejected

- Answering in prose: a model's words attach nothing and left the session stopped.
- Opening a second task for the other repository: [0066](0066-only-the-human-opens-a-task.md), one scope per task.
- Naming the project in the message text: free text cannot be validated at the door.

## Reopen when

- A session needs a repository no configured project holds.
