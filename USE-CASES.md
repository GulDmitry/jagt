# Use cases

What jagt does in a given situation, one line each. Append a line when a case turns out to be non-obvious —
that is cheaper than re-deciding it in the next session. Rules live in `CLAUDE.md`; this file is the answers.

## Starting jagt

| Situation | What to run | What happens |
|---|---|---|
| Something the setup needs is not installed or not configured | start jagt | It refuses, and lists EVERY problem at once — each naming the key that fixes it. Checked: git and tmux, the configured terminal, the editor launchers, ttyd when the web terminal is on, the projects in `config.json`, the stdio bridge when the agent is Codex, and a code host or tracker that was wired. |
| A project's `path` is missing, is not a git repository, or its `deployBranch` equals its base | start jagt | Refused, naming the project key. What lives on a REMOTE is not checked: a branch would cost a fetch per project on every start. |
| `code-host.type` / `tracker.type` has a typo, or its token is unset | start jagt | Refused. The old answer was silent: nothing claimed the URL, so every sweep and every `do` fell back to a paid model read and nobody noticed until the bill. |
| A token is set but wrong, or the host is unreachable | start jagt | NOT detected — no check reaches the network. Presence is what a start can answer; validity is the first read's business. |
| A suite or a smoke script boots the app on a machine with no desktop | `--orchestrator.startup-checks=false` | Skips the lot. What they ask about is the human's machine, and a runner is not one. |

## Starting work

| Situation | What to run | What happens |
|---|---|---|
| New ticket, normal | `do ABC-1` | Branch `ABC-1` cut from the project's base branch, worktree, agent. |
| Project not obvious from the ticket | `do ABC-1 <project>` | Same, without the label lookup. |
| Work must sit on someone else's branch | `do ABC-1 from feature/parent` | Branch cut from `feature/parent`; the merge request will TARGET it. Deploy still goes to `deployBranch`. |
| One change moves two repositories (a service and its client) | `do ABC-1 api,web` | ONE task, ONE agent session, a worktree per repository named `ABC-1-<project>`. The session runs in the first named; its briefing lists the others as its own to edit. On the board: pick several projects (ctrl/cmd-click). |
| A multi-repo task reaches review | `ship ABC-1` | A commit, a push and a request PER repository, each targeting that repository's own base branch. The sentence names each one. |
| One of its repositories has no code host configured | `ship ABC-1` | The WHOLE task falls back to instructing the agent — half pushed by jagt and half by the agent is a state neither can describe. |
| "What exactly will this deploy push?" | the board's Deploy button | The confirmation names it before anything runs: one `project → branch` line per repository, or `no deployBranch in config.json` where a project configures none. The console answers the same question afterwards, in the sentence it prints. |
| "What exactly will this revert take out?" | the board's Revert button | The confirmation names the branches it pushes to, and the scope: the last deploy only. |
| A request you have decided to land, whatever the review says | `deploy ABC-1` | Offered on ANY task with a request open — REVIEW_PENDING included. The reviewer's verdict is not jagt's gate; git's only precondition is commits on the branch. |
| A multi-repo task is ready to deploy | `deploy ABC-1` | Merged and pushed repository by repository, in the order the task holds them. The sentence names each merge. |
| One repository conflicts after another has already landed | `deploy ABC-1` | Stops there. DEPLOY_CONFLICT, and the sentence names BOTH sides — what is live on the deploy branch and what is not. Resolve in the named deploy worktree, then `deploy ABC-1` again: it continues from the repository that stalled. |
| One repository of the task has nothing to deploy (the change never touched it) | `deploy ABC-1` | Passed over and named in the sentence, not treated as a failure — that is also why starting the sequence over is harmless when the deploy worktree was cleaned up by hand. Every repository idle → refused, exactly as a single-repo task is. |
| A deploy worktree is sitting at the shared `<task>-deploy` path but a SIBLING repository cut it | `deploy ABC-1` | Refused for that repository by name: finishing someone else's merge there would push their work to this repository's remote. Resolve or `git worktree remove --force` it first. |
| A multi-repo deploy breaks off for something no worktree can fix (rejected push, failed fetch) | `deploy ABC-1` | Status untouched (nothing to resolve), but the sentence AND the task message name what is already live. Deal with the cause and `deploy` again. |
| The task was deployed more than once | `revert ABC-1` | Only the LAST deploy comes out: a deploy overwrites the one merge commit recorded per repository. The earlier rounds stay live — `git log --merges --grep ABC-1 origin/<deployBranch>`, then `git revert -m 1 <sha>` newest first. |
| Taking a multi-repo deploy back out | `revert ABC-1` | Reverse order, and only the repositories that actually landed. Each one forgets its merge commit as it comes out, so a `revert` that fails part way can be repeated and touches only what is still live — the task stays DEPLOYED until everything is out. |
| Ticket unreadable / no tracker | `do ABC-1 <project>` | The read is skipped; the task carries no title. |
| A tracker is configured and the ref is its own (`ABC-1`, `…/browse/ABC-1`) | `do ABC-1` | Title, labels and project are read over the tracker's API — no model call, no tokens. |
| A tracker is configured but the ref points somewhere else | `do <url>` | The headless assistant follows the URL (paid). Following a link into a tracker jagt was never pointed at is the one thing it still does that no configured API can. |
| A paid read must not depend on which MCP servers the human has installed today | `assistant.mcp-config` | Only the declared servers load; settings still apply, so a `${ENV}` placeholder and the model resolve as usual. Determinism only — measured, it costs MORE than inheriting, whose prompt prefix the human's own sessions keep cached. Rewrite `allowed-tools` if it was set: declared servers have no plugin prefix in their tool names. One value only — a path or the JSON itself, since a list would be split on commas. One thing such a file may NOT rely on is `${CLAUDE_PLUGIN_ROOT}`: it is set only when the plugin itself loads the file, so pointing at it directly starts nothing and the read comes back empty. |
| The configured tracker refuses the read (expired token, no access) | `do ABC-1` | Refused — jagt does NOT retry it through a paid model read, or an expired token would quietly cost money on every launch. A bare key still starts the task; only its title is missing. |
| The app needs a gitignored `.env` (or key, cert) to start | nothing — `worktree.copyGlobs` | Copied from the base repo to the same relative path, root-level files included (`**/x` matches the root too). A path the checkout already produced is left alone: git tracks it, so it is not the file that was missing, and overwriting it would leave every worktree with an uncommitted change to a tracked file. |
| On the board | the launch row, always open | Ticket, project, base branch, notes and Start. The project list comes from `config.json` on every load — no button to press first. It opens on the first project rather than a placeholder; a project is sent only if you actually pick one, so an untouched list still gets the ticket read and the label lookup. |
| The repository ships its own `CLAUDE.md` or `AGENTS.md` | `do <ticket>` | Kept untouched; the briefing goes to `CLAUDE.local.md` instead. Claude loads both. Other agents refuse the task rather than start unbriefed. |

## Looking at an agent

| Situation | What to run | What happens |
|---|---|---|
| "Which of these buttons changes something?" | the card | Two rows: what moves the task on (ship … done) above, the ones that only look or restart below. The split is `TaskAction.Group`, so the card cannot group them one way and a future surface another. |
| The agent is asking something | `focus <task>` | Its tmux window is selected. In the console that raises the terminal the viewer runs in; on the board, with `orchestrator.web-terminal` on, the session opens OVER the board and you type into it there. |
| No web terminal configured | Focus, on the board | The same selection, and the sentence names the window the session is in — there is nothing to embed. |
| Panel closed by mistake | — | Nothing stops. The agent lives in tmux; the terminal server ends with the last panel watching it, and Focus starts another. |
| You want the board from a second machine | `--server.address=0.0.0.0` | Refused by default, and deliberately: the board needs no password to deploy, close a task or start an agent. Widen the bind only on a network you trust, and remember the embedded terminal is a shell (`web-terminal.bind` decides that one separately). |
| Panel open on one task, Focus pressed on another | Focus | The panel follows: in viewMode `shared` every task is a window of ONE session, and a session has one current window — for the embedded view and the native viewer alike. |
| ttyd not installed, or its port taken | — | Focus still selects the window; the panel simply does not open, and the log carries ttyd's own exit code. |
| A task at SHIPPING | — | The status and the move line are the whole answer; the detail line stays empty. A third wording of "the agent is pushing" told nobody anything. |
| The backend restarts while an agent session is live (HTTP transport) | — | Nothing to do: the next tool call reaches the new process. A call made while it was down answers "Unable to connect", and the one after the restart succeeds — a failed call does not retire the server for the session. |

## Review requests

| Situation | What to run | What happens |
|---|---|---|
| Take over an existing request (reopened, or someone else's) | `resume <request-url>` | The request is the only input: its SOURCE branch becomes the task, its TARGET becomes the base. Status CI_POLLING. |
| The request does not target the base branch | `resume <request-url>` | Nothing special — the request's own target is stored, so the next `ship` updates THAT request instead of opening a second one. |
| The request's source branch already belongs to a task | — | Refused: a task IS its branch, so two tasks cannot share one. Work in the existing task (`focus`/`open_task_tab`), or `do <ticket>` for a new branch. |
| `do <ticket>` on a branch that already exists, and the base repository holds it | pick `recreate` or `resume` | The refusal comes FIRST: nothing is freed and nothing is moved, because a run that answers "decide what to do" must leave the repository exactly as it found it. |
| A new ticket whose work happens to live on an older task's branch | `do <new-ticket> from <that-branch>` | A new task/branch of its own; the old request stays with the old branch. |
| The request's source branch is not a legal task name (`feature/x`) | — | Refused with that branch named: a task IS its branch, and the name becomes a directory and a tmux window too. |
| The request URL belongs to the configured code host | `resume <request-url>` | Its branches and title are read over that host's API — no model call, no tokens. |
| The request lives on a host jagt was never pointed at | `resume <request-url>` | The headless assistant follows the URL (paid) — the same fallback a ticket read has. |
| The configured host claims the URL and the read fails | — | Refused, NOT retried through a paid read: an expired token would otherwise cost money on every attempt while looking healthy. |
| Request unreadable (no code host, assistant failed) | — | Refused. A guessed branch name would point the task at a branch the request does not track. |
| The review lives on GitHub (`code-host.type=github`) | `sweep <task>` / the auto-poll | Threads, the approval decision and the head commit's check rollup come from one GraphQL query — REST cannot say whether a thread is resolved, and a round that cannot tell would re-relay every comment forever. |
| A GitHub reviewer wrote the request in the review body, with no inline thread | `sweep <task>` | Relayed all the same — review bodies come first in the round, and a "changes requested" decision never comes back as "nothing to answer" (which would advise `deploy`). |
| A GitHub repository requires no review, and someone approved | `sweep <task>` | Counted as approved: the host reports no decision at all on an unprotected repo, so the reviewers' own latest states are read instead. |
| A ship opens the request on GitHub | `ship <task>` | Squash and delete-branch-on-merge are NOT sent: they are repository settings there, and jagt configures no repository. Set them once on the repo. |
| The BASE repository still has the branch checked out | nothing | Freed automatically: it is detached IN PLACE, so its files do not change, and a WARN names the branch it was on. The branch moves to the task's worktree, which is where you work on it now; if creating the task then fails, the checkout is put back. |
| The request targets a branch that has since been DELETED | `resume <request-url>` | Works: a resume is cut from nothing, so the base repository is detached where it already stands instead of at the dead target. Only the next `ship` needs a target that still exists. |
| That checkout has uncommitted TRACKED changes, or another worktree holds the branch | commit/stash, or free that worktree | Refused, naming the directory: a switch carries tracked changes with it, and another task's worktree is not jagt's to move. Untracked files do not block anything. Nothing is registered, so the retry is clean. |

## Review rounds

| Situation | What the agent does |
|---|---|
| Comment is right | Fixes it locally. Never commits or pushes on its own. |
| Comment is wrong | Changes nothing, replies with the one technical reason. Silent compliance is the failure mode this exists to prevent. |
| Comment is unclear, or forces a design decision | Asks: `notify_user` + REVIEW_PENDING with message `awaiting: …`. The board shows NEEDS INPUT instead of the link. |
| Checks red, no comments | Fixes the build locally, then REVIEW_PENDING — it cannot push, so it never sees the checks go green. |
| Every comment was already handled, or pushed back on | REVIEW_PENDING with message `no changes: …`. Nothing is highlighted, the line reads ANSWERED, and jagt does NOT advise a ship — there is no diff, and shipping would return the task to CI_POLLING for the poll to relay the same threads. |
| Drafted replies exist | Both surfaces flag it, and the push notification names the file — a banner has nothing beside it to show them; they are posted only after a human `ship`. |
| A thread the agent FIXED | Resolved at ship time, by the agent's own MCP — an unresolved thread is relayed by every later round. |
| A thread the agent disagreed with or asked about | Left UNRESOLVED on purpose: resolving it would read as agreement, and settling it is the reviewer's move. |
| The reviewer never resolves the threads | The task simply sits at REVIEW_PENDING (the auto-poll only watches CI_POLLING), so nothing is re-briefed and nothing is paid for. `sweep <task>` re-checks when you want. |
| The pipeline goes red while the request is still open | The sweep stamps it: the card shows a red dot (its title is the host's own wording), the console line reads `CHECKS RED · …`, and one desktop notification arrives the first time that run goes red. `sweep` relays the failure to the agent. |
| You type `review <task>` out of habit | It runs the sweep: the verb was renamed and the old spelling still resolves, deliberately absent from `help` so only one word is advertised. |
| "Is anything actually polling?" | — | Both surfaces answer without being asked: the console's dashboard header and the board's chip carry `auto-review on` (or `auto-review off`), and every task out for review carries its own `next poll in 4m`. The stamp is absolute, so the countdown stays right between fetches. |
| A task sits at CI_POLLING and nothing is polling it | — | The card says which of the two reasons it is: `off for this task` (its own `autoReview` is false — the flag is stamped at creation, so a task created while polling was off keeps it) or `cannot time this round (no stamp)` (a request from a `state.json` older than the round stamp). Either way `sweep` it by hand. |
| Polling stopped and nothing is happening | — | The window elapsed (`autoReview.windowHours`, default 24). Both surfaces say `window elapsed` on the task, and a desktop ping said it once when it happened. |
| A big round (tens of threads) and no `orchestrator.code-host` configured | Expect comments to go MISSING: the paid read returned 5 of 9 when measured, so configure the host before trusting a round. A read that loses them all reads as a clean review and advances the task. |

## Finishing

| Situation | What to run | What happens |
|---|---|---|
| Ship a round | `ship <task>` | Commits, pushes the task branch, opens/updates the request. Never merges. |
| Deploy | `deploy <task>` | Merges the task branch into `deployBranch` and pushes. Refused when that equals the base branch. |
| Deploy hit a conflict | resolve in the deploy worktree | Status DEPLOY_CONFLICT; jagt keeps the half-done state for you. |
| Take a deploy back out | `revert <task>` | Reverts the LAST recorded merge commit. Refused (with a by-hand recipe) whenever it would have to guess which commit. |
| Done | `done <task>` | Kills the agent window, reaps its language server. The branch survives. |
| Merged task branches pile up | your own git, per branch | jagt has no `prune`: a cross-project bulk delete was removed deliberately. Cleanup is one task's own business. |
| Someone types `prune all` anyway | — | Answered by name, before any model call: a retired verb must never be MAPPED onto a live one (`done <task>` is the near neighbour, and it kills a worktree). |
| "Is it me holding these up?" | `stats` | Second section: per task, how long it has been on you, on its agent and on the code host, plus the rounds it has been out for review — and one line naming the slowest of the three. |
| The same numbers, a week later | — | Not available: `done` removes the task, so its history goes with it. `stats` describes the OPEN work, never throughput over time. |
