# Guards: what each stops, what stands behind it, what it passes

[← AGENTS.md](../../AGENTS.md)

A review judges a check against this file. The records it condenses keep the evidence:
[0022](../decisions/0022-the-resume-rebase-is-the-one-rewrite-and-the-guard-is-a-guardrail.md),
[0026](../decisions/0026-the-guards-gaps-after-round-three.md) to
[0031](../decisions/0031-the-host-cli-is-judged-wherever-it-stands.md),
[0040](../decisions/0040-git-runs-only-what-the-human-trusted.md),
[0044](../decisions/0044-a-session-writes-only-inside-its-task.md),
[0230](../decisions/0230-contributor-guards-are-tracked-shell-the-loop-the-backlog-and-the-owners-limits-stay-private.md).

## A boundary is an allow-list, a guardrail is a block-list

| check | judges | reads by | behind it |
|-------|--------|----------|-----------|
| `ReadGate` | each call of a headless read and the Master's reviewer | allow-list, fails closed | nothing: it is the boundary |
| `NotThePage` | a foreign Host or Origin, and every board write but `/mcp` and `/api/agent/` | allow-list | the Master's token, same-user trust |
| `auth: password` (Spring Security) | the board, `/api` and its streams, from anyone off loopback | login; `/mcp`, `/api/agent/` and the login page's files exempt | `NotThePage` first, one bcrypt hash |
| `LoopbackOrRootToken` | `/mcp` and `/api/agent/` under `auth: password`, from a peer off loopback or forwarded | allow-list: the Master token | `NotThePage` first; loopback peers pass as ever |
| `ToolGate` | a session's shell line or a plan's command probe, `git worktree add` included | block-list over one line, fails open | the worktree's `pre-push` |
| `PlanFirst` | a session's file write while its plan waits | allow-list: `plan.md`, `task_visual.md`, `task_notes.md` | the human, who reads the diff |
| `HostCliLine` | `gh`/`glab` wherever a line names them | a word reader, not a shell | the code host's branch protection |
| `.jagt/hooks/pre-push` | a push leaving a session's worktree | refusals the session could edit | the code host's branch protection |
| `TaskOffers` git-trust read (`GitDistrust`) | `ship`, `deploy`, `revert`, a Master read | a digest per config key and hook, against the trusted one | the human's `trust` |
| CLI's OS sandbox, opt-in | a session's shell writing | path allow-list (`WorktreeSandbox`) | the human, asked by the CLI |

`ToolGate` fails open, `ReadGate` closed: a stopped jagt refusing a session's every call would read as a rule.
The CLI's `onFailure: "block"` (2.1.309) would close it, and stays off for that reason.

## Contributor guards: `scripts/guards`, shell on stdin, exit 2 blocks, any other outcome passes

| guard | judges | block-list | behind it | known to pass |
|-------|--------|------------|-----------|---------------|
| `no-poll-loop.sh` | a Bash call of any session | `until`/`while`/`for` whose body sleeps; `watch` | nothing: a wasted turn | a poll inside a script or function the line runs; one `sleep`; quoted text, heredoc bodies |
| `no-shared-kill.sh` | a Bash call of any session | `gradle --stop`; `pkill`/`killall` of java or gradle; docker `prune`, kill/stop of `ps` output; `kill` of `$PPID` or a named pid variable | nothing: a killed run restarts | a numeric pid; a name built at runtime; other docker spellings |
| `push-after-local-ci.sh` | a `git push` from `release`, a hotfix branch or to them | HEAD's tree (refspec source, `-C` honoured) lacking the `ci-local.ok` record | CI and the host's branch protection | a quoted or runtime-built push; a hand-written record |
| `reviewer-read-only.sh` | a Bash call of the reviewer agent | `git` writes, `rm`, `mv`, `cp`, `touch`, `mkdir`, `ln`, `chmod`, `tee`, `sed -i`, `perl -i`, `curl -o`, any `>` (quoted too) | nothing: the reviewer reads a range | a write by `python3 -c`, `dd`, `rsync`, `install`, `truncate`, `awk`; a `git` alias; a line built at runtime |

A guard parses no shell grammar ([0028](../decisions/0028-what-the-guards-still-pass-after-round-four.md)):
a `>` inside quotes stays refused, a spelling it cannot read passes.
Its promises are bats cases; one left without a case is a row of `contract-gaps.txt`.

## What a review reports

- **A new spelling of a class below is not a finding.** Refusing it is optional work and never blocks a release.
- A finding is a class missing below, a Reopen condition that now holds, or an allow-list that widened.
- A gap that joins the list carries its Reopen condition, in the commit refusing what it can.
- `finished` sums what `ToolGate`, the report door and the git-trust read refused, per kind; the `pre-push` hook's refusals stay uncounted.
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
| A same-user process rewriting `state.json` together with the verdict file | sessions run as another user |
| A same-user process forging Origin, reading `master-token`, naming another worktree's directory | sessions run as another user |
| The board reached with no port spelled: port 80, `nc 127.0.0.1 8290`, `/dev/tcp/…`, still forging Host and Origin | a write lands that way |
| `HEAD` moved on an earlier call, then pushed | the `pre-push` hook is bypassed too |
| A board behind an ingress: one bcrypt password, no throttle | a second login mode or a brute-force report |
| Config the git-trust read reads no further: global, system, an `include.path` file, `branch.*` tracking | a guarded git runs a program set there |
| A hook the merged branch carries under a relative `core.hooksPath` | a deploy runs a hook the record did not hold |
| A headless answer in prose, its object in a fence and no schema call made | such an answer carries a tool's output |
| An MCP read sends its arguments out; a local server's read verb reads outside the worktrees | a read tool writes, or one named takes a path |
| An excluded command, the Edit tool, an MCP server, a Unix socket | a write lands outside the task so |
| A file written through the shell while the plan waits: `tee`, `sed -i`, a redirect | shell writes during planning are seen in practice |
| A runtime declaring no `gate` event: Codex keeps its read-only sandbox for a plan | its CLI gains a hook jagt can answer |
| A host write on an address no read of the host returned: only `closeRequest` is judged against the remote's host | a `retryFailed` address comes from a session, or a write is added that takes one |

Known to refuse: `git -C` on a worktree whose path holds a space. Reopen when a round's second worktree has one.
