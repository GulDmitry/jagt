# 0044 — A session writes only inside its task

[← decisions](README.md) · rule: [`guards`](../rules/guards.md)

## Status

Accepted 2026-10-15. "On by default" superseded by [0092](0092-the-session-sandbox-is-opt-in.md): opt-in.
Reopens nothing; holds [0025](0025-the-board-is-no-way-around-the-token.md)'s sandbox condition
open.

## Context

- A session's shell could harm the machine beyond its task; only the agent CLI's permission rules stood there.
- 30 past task sessions, 9113 shell calls: `/tmp`, Gradle and Maven caches, `docker` (15 tasks), `kubectl`/`oc`
  (7), an ssh tunnel, writes into a sibling worktree and into another project's repository.
- A spike on Claude 2.1.292, macOS: writes outside the allow-list refused; a commit into the base repository's git
  directory, HTTPS to any host, a local port, tmux all pass. ssh and raw TCP fail: no DNS in the sandbox, no key
  to open its network. `kubectl` needs the OS trust store (`enableWeakerNetworkIsolation`). A symlinked path such as
  macOS's `/tmp` allows nothing.

## Decision

- The agent CLI's own OS sandbox, opt-in (`sandbox.enabled: true`): Claude's `sandbox` block, Codex's `workspace-write` with
  `network_access`. A write list, not a network list: every host stays reachable.
- jagt writes the list (`WorktreeSandbox`): the task's worktrees and git directories, `/tmp`, the tool caches, the
  human's `sandbox.allowWrite`, each resolved to its real path.
- git's remote verbs and `ssh` run outside Claude's sandbox (`excludedCommands`), still judged by `ToolGate` and the
  `pre-push` hook; the human adds a command in `sandbox.excludedCommands`.
- Anything else asks through the CLI's own prompt, which reaches the board as `waiting`.
- `failIfUnavailable`: a session never runs unsandboxed unseen; on Linux a start without `bwrap` and `socat` is
  refused (`ClaudeSandboxCheck`).

## Rejected

- Refusing writes in `ToolGate`: a block-list over one line (0028, 0031).
- A domain allow-list: every project would keep one, and setup would stop being one copied file.

## Reopen when

- Claude's sandbox gains a key opening its network: drop the ssh exclusions.
- A Codex session stalls on a write it needs: `approval_policy = "never"` leaves it no prompt to ask through.
- A denied read of `.jagt/master-token` is wanted: 0025's token would then become a boundary.
