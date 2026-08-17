# Use cases

What jagt does in a given situation, one line each. Append a line when a case turns out to be non-obvious —
that is cheaper than re-deciding it in the next session. Rules live in `CLAUDE.md`; this file is the answers.

## Starting work

| Situation | What to run | What happens |
|---|---|---|
| New ticket, normal | `do ABC-1` | Branch `ABC-1` cut from the project's base branch, worktree, agent. |
| Project not obvious from the ticket | `do ABC-1 <project>` | Same, without the label lookup. |
| Work must sit on someone else's branch | `do ABC-1 from feature/parent` | Branch cut from `feature/parent`; the merge request will TARGET it. Deploy still goes to `deployBranch`. |
| Ticket unreadable / no tracker | `do ABC-1 <project>` | The read is skipped; the task carries no title. |
| On the board | the launch row, always open | Ticket, project, base branch, notes and Start. The project list comes from `config.json` on every load — no button to press first. It opens on the first project rather than a placeholder; a project is sent only if you actually pick one, so an untouched list still gets the ticket read and the label lookup. |

## Review requests

| Situation | What to run | What happens |
|---|---|---|
| Take over an existing request (reopened, or someone else's) | `resume <mr-url>` | The request is the only input: its SOURCE branch becomes the task, its TARGET becomes the base. Status CI_POLLING. |
| The request does not target the base branch | `resume <mr-url>` | Nothing special — the request's own target is stored, so the next `ship` updates THAT request instead of opening a second one. |
| The request's source branch already belongs to a task | — | Refused: a task IS its branch, so two tasks cannot share one. Work in the existing task (`focus`/`open_task_tab`), or `do <ticket>` for a new branch. |
| A new ticket whose work happens to live on an older task's branch | `do <new-ticket> from <that-branch>` | A new task/branch of its own; the old request stays with the old branch. |
| The request's source branch is not a legal task name (`feature/x`) | — | Refused with that branch named: a task IS its branch, and the name becomes a directory and a tmux window too. |
| Request unreadable (no code host, assistant failed) | — | Refused. A guessed branch name would point the task at a branch the request does not track. |
| The branch is still checked out somewhere (the base repo, an editor, an old worktree) | free it, then `resume` again | Refused by NAME: git allows one checkout per branch, so the message says which directory holds it and the `git -C <dir> switch` that frees it. Nothing is registered, so the retry is clean. jagt will not switch a checkout that may hold uncommitted work. |

## Review rounds

| Situation | What the agent does |
|---|---|
| Comment is right | Fixes it locally. Never commits or pushes on its own. |
| Comment is wrong | Changes nothing, replies with the one technical reason. Silent compliance is the failure mode this exists to prevent. |
| Comment is unclear, or forces a design decision | Asks: `notify_user` + REVIEW_PENDING with message `awaiting: …`. The board shows NEEDS INPUT instead of the link. |
| Pipeline red, no comments | Fixes the build locally, then REVIEW_PENDING — it cannot push, so it never sees the pipeline go green. |
| Every comment was already handled, or pushed back on | REVIEW_PENDING with message `no changes: …`. Nothing is highlighted, the line reads ANSWERED, and jagt does NOT advise a ship — there is no diff, and shipping would return the task to CI_POLLING for the poll to relay the same threads. |
| Drafted replies exist | Both surfaces flag it; they are posted only after a human `ship`. |
| A thread the agent FIXED | Resolved at ship time, by the agent's own MCP — an unresolved thread is relayed by every later round. |
| A thread the agent disagreed with or asked about | Left UNRESOLVED on purpose: resolving it would read as agreement, and settling it is the reviewer's move. |
| The reviewer never resolves the threads | The task simply sits at REVIEW_PENDING (the auto-poll only watches CI_POLLING), so nothing is re-briefed and nothing is paid for. `review <task>` re-checks when you want. |
| A big round (tens of threads) and no `orchestrator.code-host` configured | Expect comments to go MISSING: the paid read returned 5 of 9 when measured, so configure the host before trusting a round. A read that loses them all reads as a clean review and advances the task. |

## Finishing

| Situation | What to run | What happens |
|---|---|---|
| Ship a round | `ship <task>` | Commits, pushes the task branch, opens/updates the request. Never merges. |
| Deploy | `deploy <task>` | Merges the task branch into `deployBranch` and pushes. Refused when that equals the base branch. |
| Deploy hit a conflict | resolve in the deploy worktree | Status DEPLOY_CONFLICT; jagt keeps the half-done state for you. |
| Take a deploy back out | `revert <task>` | Reverts the recorded merge commit. Refused (with a by-hand recipe) whenever it would have to guess which commit. |
| Done | `done <task>` | Kills the agent window, reaps its language server. The branch survives. |
| Merged task branches pile up | your own git, per branch | jagt has no `prune`: a cross-project bulk delete was removed deliberately. Cleanup is one task's own business. |
| Someone types `prune all` anyway | — | Answered by name, before any model call: a retired verb must never be MAPPED onto a live one (`done <task>` is the near neighbour, and it kills a worktree). |
