# Guards: what each stops, what stands behind it, what it passes

[← AGENTS.md](../../AGENTS.md)

A review judges a guard against this file. The records it condenses keep the evidence:
[0022](../decisions/0022-the-resume-rebase-is-the-one-rewrite-and-the-guard-is-a-guardrail.md),
[0026](../decisions/0026-the-guards-gaps-after-round-three.md) to
[0031](../decisions/0031-the-host-cli-is-judged-wherever-it-stands.md),
[0040](../decisions/0040-git-runs-only-what-the-human-trusted.md),
[0044](../decisions/0044-a-session-writes-only-inside-its-task.md).

## A boundary is an allow-list, a guardrail is a block-list

| guard | judges | reads by | behind it |
|-------|--------|----------|-----------|
| `ReadGate` | each call of a headless read and the Master's reviewer | allow-list, fails closed | nothing: it is the boundary |
| `BoardWriteFilter` | every board write but `/mcp` and `/api/agent/` | allow-list | the Master's token, same-user trust |
| `ToolGate` | a session's shell line or a plan's command probe | block-list over one line | the worktree's `pre-push` |
| `PlanFirst` | a session's file write while its plan waits | allow-list: `plan.md`, `task_visual.md`, `task_notes.md` | the human, who reads the diff |
| `HostCliLine` | `gh`/`glab` wherever a line names them | a word reader, not a shell | the code host's branch protection |
| `.jagt/hooks/pre-push` | a push leaving a session's worktree | refusals the session could edit | the code host's branch protection |
| `GitStateGuard` | `ship`, `deploy`, `revert`, a Master read | a digest per config key and hook, against the trusted one | the human's `trust` |
| CLI's OS sandbox | a session's shell writing | path allow-list (`WorktreeSandbox`) | the human, asked by the CLI |

## What a review reports

- **A new spelling of a class below is not a finding.** Refusing it is optional work and never blocks a release.
- A finding is a class missing below, a Reopen condition that now holds, or an allow-list that widened.
- A gap that joins the list carries its Reopen condition, in the commit refusing what it can.
- `finished` sums what `ToolGate`, the report door and `GitStateGuard` refused, per kind; the `pre-push` hook's refusals stay uncounted.
- Never proposed again, each rejected with its reason: a shell grammar in `ToolGate` (0028, 0031), state across
  lines (0026), `gh`/`glab` writes refused verb by verb (0029), `git -c` in the reviewer (0028).

## Known to pass, by class

| class | Reopen when |
|-------|-------------|
| A line built at runtime: a variable, a script, `xargs`, `${IFS}`, an expanded brace or glob | a push or host-CLI write reaches a shared branch so |
| A line another program runs: piped into a shell, `python3 -c`, `osascript -e`, a multiplexer | the same |
| A git alias, in a config already or set on the line as `!` | the same |
| Any spelling `HostCliLine` reads differently from the shell | a host-CLI write runs past `ToolGate` by a spelling its rules read |
| A binary other than git, `gh`, `glab` speaking a host's API: `curl`, a runtime | a shared ref moves without `git push` |
| A session editing its own `.jagt/hooks` | a hook differs from what jagt wrote |
| A same-user process forging Origin, reading `.jagt/master-token`, naming another worktree's directory | sessions run as another user |
| The board reached with no port spelled: port 80, `nc 127.0.0.1 8290`, `/dev/tcp/…`, still forging Host and Origin | a write lands that way |
| `HEAD` moved on an earlier call, then pushed | the `pre-push` hook is bypassed too |
| A non-loopback `server.address`: any host there acts as the human | the board leaves loopback by default |
| Config `GitStateGuard` reads no further: global, system, an `include.path` file, `branch.*` tracking | a guarded git runs a program set there |
| A hook the merged branch carries under a relative `core.hooksPath` | a deploy runs a hook the record did not hold |
| A headless answer in prose, its object in a fence and no schema call made | such an answer carries a tool's output |
| An MCP read sends its arguments out; a local server's read verb reads outside the worktrees | a read tool writes, or one named takes a path |
| An excluded command, the Edit tool, an MCP server, a Unix socket | a write lands outside the task so |
| A file written through the shell while the plan waits: `tee`, `sed -i`, a redirect | shell writes during planning are seen in practice |
| A runtime declaring no `gate` event: Codex keeps its read-only sandbox for a plan | its CLI gains a hook jagt can answer |

Known to refuse: `git -C` on a worktree whose path holds a space. Reopen when a round's second worktree has one.
