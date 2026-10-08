# 0027 — A headless call is fenced by jagt

[← decisions](README.md) · rule: [`master`](../../openspec/specs/master/spec.md)

## Status

Accepted 2026-10-08. Its gaps listed in [0028](0028-what-the-guards-still-pass-after-round-four.md).

## Context

- The Master's reviewer ran `--tools Read,Grep,Glob,Bash` in `dontAsk` under `--setting-sources user`, so every
  user-level allow rule applied. On the owner's machine those allow `Bash(awk:*)`, `Bash(tmux:*)`, `Bash(curl -s:*)`:
  a diff could run `awk 'BEGIN{system(…)}'`, type into the Master's window, or send data out. Read had no path
  limit: `~/.ssh`, `.jagt/master-token`.
- A ticket read inherited allows for whole servers (a browser's), and the verb deny-list missed `new_page`,
  `hover`, `state_clear`, `notepad_write_*`.
- A deny list can only name what someone thought of; an allow rule outranks nothing a hook denies.

## Decision

- Every headless run passes a `PreToolUse` hook through `--settings`, posting each call to
  `/api/agent/read/<fence>`. `ReadGate` answers from the run's `ReadScope`: the tools it names, file tools inside
  its roots, one read-only git command there. Anything else is denied, and a hook's deny beats any allow rule.
- It fails closed, unlike a session's gate: an unknown fence is refused, and a jagt that does not answer makes
  the hook exit 2, which blocks the call. A read runs only while jagt does.
- `--allowedTools` and the verb deny-list stay, as the second line.
- Proven with `claude -p` on haiku: a user-allowed `awk` and a whole-allowed browser tool denied, a Read and
  `git log` inside the worktree run, every call refused with the gate down.

## Rejected

- A shipped hook script: a second gate beside the session's, untested by the suite.
- The scope written into the hook command: a hundred plugin tools pass the server's header limit.
- An `http` hook: a connection failure does not block, so it fails open.

## Reopen when

- A headless call runs a tool its scope does not name.
- The CLI stops blocking on a hook's exit 2.
