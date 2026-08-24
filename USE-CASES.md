# Use cases

**One line per situation.** Append a row when a case turns out to be non-obvious — that is cheaper than
re-deciding it next session. The rules live in [`AGENTS.md`](AGENTS.md), the map in
[`ARCHITECTURE.md`](ARCHITECTURE.md); this file is the answers.

- [Starting jagt](#starting-jagt)
- [Starting work](#starting-work)
- [Multi-repo tasks](#multi-repo-tasks)
- [Reading a ticket](#reading-a-ticket)
- [Watching an agent](#watching-an-agent)
- [Reading the board](#reading-the-board)
- [Review requests](#review-requests)
- [Review rounds](#review-rounds)
- [Auto-review](#auto-review)
- [Deploy and revert](#deploy-and-revert)
- [Finishing](#finishing)

## Starting jagt

| situation | run | what happens |
|---|---|---|
| Something the setup needs is missing or unconfigured | start jagt | Refused, with **every** problem at once, each naming the key that fixes it |
| A project path is missing, is no git repository, or `deployBranch` equals the base | start jagt | Refused, naming the project key |
| `code-host.type` / `tracker.type` has a typo, or its token is unset | start jagt | Refused — otherwise every read silently falls back to a paid model call |
| A token is set but wrong, or the host is unreachable | start jagt | Not detected. No check reaches the network; validity is the first read's business |
| A suite or smoke script boots on a machine with no desktop | `--orchestrator.startup-checks=false` | Skips them all — they ask about a human's machine, and a runner is not one |
| You work **on** jagt with Codex or Qwen rather than Claude | open the repository | Same rules, same MCP server: `AGENTS.md` is the one knowledge file, and each CLI has its own server declaration committed at the root |

What a start deliberately does not check: anything on a remote (a branch would cost a fetch per project on
every start) and anything over the network.

## Starting work

| situation | run | what happens |
|---|---|---|
| New ticket, normal | `do ABC-1` | Branch `ABC-1` cut from the project's base branch, worktree, agent |
| Project not obvious from the ticket | `do ABC-1 <project>` | Same, without the label lookup |
| Work must sit on someone else's branch | `do ABC-1 from feature/parent` | Cut from that branch, and the request targets it. Deploy still goes to `deployBranch` |
| A new ticket whose work lives on an older task's branch | `do ABC-1 from <that-branch>` | A new task and branch of its own; the old request stays with the old branch |
| The base repository still has the branch checked out | nothing | Freed automatically — detached in place, so its files do not change, and a WARN names the branch |
| That checkout has uncommitted **tracked** changes, or another worktree holds the branch | commit/stash, or free that worktree | Refused, naming the directory. Untracked files block nothing; nothing is registered, so the retry is clean |
| `do <ticket>` on a branch that already exists | pick `recreate` or `resume` | The refusal comes first — nothing is freed or moved until you decide |
| The app needs a gitignored `.env`, key or cert to start | nothing — `worktree.copyGlobs` | Copied to the same relative path, root-level files included. A path the checkout already produced is left alone |
| The repository ships its own `CLAUDE.md` or `AGENTS.md` | `do <ticket>` | Kept untouched; the briefing goes to `CLAUDE.local.md`. Other agents refuse rather than start unbriefed |
| The project ships its own `.codex/config.toml` | `do <ticket>` with `agent=codex` | Left alone: `CODEX_HOME` points at `.jagt/codex/` in the worktree, so no tracked file changes |
| On the board | the launch row, always open | Ticket, project, base branch, notes, Start. A project is sent only if you pick one |

## Multi-repo tasks

| situation | run | what happens |
|---|---|---|
| Two repositories that move **independently** | two tasks | Every verb is per task; one task with two statuses is not a thing |
| One change moving two repositories (a service and its client) | `do ABC-1 api,web` | One task, one agent session, a worktree per repository. The session runs in the first named |
| It reaches review | `ship ABC-1` | A commit, push and request **per repository**, each targeting its own base branch |
| One of its repositories has no code host configured | `ship ABC-1` | The whole task falls back to instructing the agent — half by jagt and half by the agent describes nothing |
| A round comes back | `sweep ABC-1` | Merged as the **least finished** repository: approved only when all are, the pipeline the worst one |
| It is ready to deploy | `deploy ABC-1` | Merged and pushed repository by repository, in the order the task holds them |
| One repository conflicts after another landed | `deploy ABC-1` | Stops there, DEPLOY_CONFLICT, and the sentence names both sides. Resolve, then `deploy` again — it continues from the one that stalled |
| One repository has nothing to deploy | `deploy ABC-1` | Passed over and named, not a failure. Every repository idle → refused, as a single-repo task is |
| The deploy breaks off for something no worktree can fix | `deploy ABC-1` | Status untouched, but the sentence and the task message name what is already live |
| Taking it back out | `revert ABC-1` | Reverse order, only what landed. Each forgets its merge commit as it comes out, so a repeat touches only what is still live |
| On the card | — | One `<project> MR` link per repository and no age on any: there is one stamp for the whole task |

A deploy worktree lives at the shared `<task>-deploy` path, so the directory alone decides nothing:

| situation | what happens |
|---|---|
| A **sibling repository** cut that worktree | Refused for that repository by name — finishing their merge would push their work to this remote |
| `deploy` keeps refusing the same way however often you press it | It was an editor, not a repository: `ide` writes project files back into the emptied directory. That residue is now deleted and the deploy carries on |
| The path holds anything else | Named instead, and a repeat is advised only when something landed |

## Reading a ticket

| situation | run | what happens |
|---|---|---|
| A tracker is configured and the ref is its own | `do ABC-1` | Title, labels and project read over the tracker's API — no model call |
| A tracker is configured but the ref points elsewhere | `do <url>` | The headless assistant follows the URL (paid) — the one thing no configured API can do |
| The configured tracker refuses the read | `do ABC-1` | Refused, and **no task is created**. It is not retried through a paid read |
| The read says "does not exist" for a ticket that plainly does | `do ABC-1` | Asked again — 5 attempts, 2s apart, at most two minutes. A tracker's own 404 is a fact and is not re-asked |
| The read answers with no key, no title or no link | `do ABC-1` | No task, and the sentence says why. A card whose ticket link is missing cannot be repaired later |
| The item genuinely has no summary | `do ABC-1` | The read **writes** a title, at most eight words, from the description. A url is never invented |
| The read answers about a different key | `do ABC-1` | Refused, naming both. The key becomes a branch, a directory and a tmux window |
| No tracker at all | `do ABC-1 <project>` | The read is skipped; the task carries no title |
| A paid read must not depend on today's MCP servers | `assistant.mcp-config` | Only the declared servers load. Determinism only — it costs **more** than inheriting |

> [!NOTE]
> `assistant.mcp-config` takes one value: a path, or the JSON itself. Rewrite `allowed-tools` if it was set —
> declared servers have no plugin prefix in their tool names. Such a file may not rely on
> `${CLAUDE_PLUGIN_ROOT}`: it is set only when the plugin loads the file itself.

## Watching an agent

| situation | run | what happens |
|---|---|---|
| An agent stops mid-work to ask | — | Its `outcome=question` report turns the card over to you: NEEDS INPUT, and one desktop ping the first time it asks |
| An agent stops and never says so | — | The watchdog probes (stale MCP + the session's own log), stamps the task, and the card turns over: NEEDS YOU, Focus highlighted |
| A session sits at a permission prompt | — | Its own hooks report it within seconds, and its log stops growing either way — the card turns over with the agent saying nothing |
| A worktree created before the hooks existed | recreate the task | Its settings file is written once, at `initialize_task`; the log a session keeps still answers, one threshold later |
| The agent CLI never came up at all | `focus <task>` | The card says so in those words rather than "no sign of life": at NEW nothing has reported yet, so the launch is what to look at |
| The agent is asking something | `focus <task>` | Its tmux window is selected — over the board with `web-terminal` on, in the viewer otherwise |
| No web terminal configured | Focus | The same selection; the sentence names the window. There is nothing to embed |
| Panel closed by mistake | — | Nothing stops. The agent lives in tmux; Focus starts another terminal server |
| ttyd not installed, or its port taken | — | Focus still selects the window; the panel does not open, and the log carries ttyd's exit code |
| Panel open on one task, Focus pressed on another | Focus | The panel follows: in viewMode `shared` every task is a window of one session |
| You want the board from a second machine | `--server.address=0.0.0.0` | Refused by default — the board needs no password to deploy or start an agent. `web-terminal.bind` is decided separately |
| The backend restarts while a session is live (HTTP transport) | — | Nothing to do: the next tool call reaches the new process |

> [!IMPORTANT]
> A worktree is briefed once, at creation. A worktree created before a brief changed keeps the old wording —
> answer in the window, or recreate the task.

## Reading the board

| situation | what it means |
|---|---|
| "Where is my task?" | Where it was. Cards are a grid ordered by alias, and a card never moves because its status changed |
| "It jumped anyway" | Your own action: a task was created or closed. An alias is the lowest free number |
| Finding one task among many | Type in the filter box (`/`): alias, ticket number or title. `Esc` clears. There is no sort control |
| "This card said 17h, I restarted the agent, now it says 0m" | The clock beside the status is time in **that** status. A fresh session re-reports itself, and those are real transitions |
| "How long has this request been hanging?" | The `MR 8h` chip — the request's own creation time. It survives rounds, respawns and restarts |
| "What does this status mean?" | It says itself: `out for review`, `not shipped`, `not approved`. A status names a state; the highlighted button says what to do |
| Why only some cards say whose move it is | The badge is for **your** move alone. Every other owner is the status word again |
| What "active" is for | Liveness for the watchdog — any MCP call bumps it, keep-alives included. A console column and a tooltip line, never a card row |
| A bare duration on a card | There is none. The status's age lives inside its chip; everything else is labelled (`MR 5d`, `↻ 7m`) |
| Where the ticket and request links are | On the things that already named them: the task number opens the ticket, the `MR <age>` chip the request |
| A line under a card repeating the request link | There is none: the card links the request and dots the checks. What is left on that line is news only — NEEDS INPUT, ANSWERED, PROBLEM, NEEDS YOU |
| A request whose stored link is not a web URL | `PROBLEM: review request link unusable: …` — nothing can follow it |
| A task at SHIPPING | The status and the move line are the whole answer; the detail line stays empty unless the watchdog found it silent |
| Clicking a desktop notification | Opens the board filtered to that task. Needs `terminal-notifier` and a board being served |
| "Which of these buttons changes something?" | Two rows: what moves the task on above, what only looks or restarts below (`TaskAction.Group`) |

## Review requests

| situation | run | what happens |
|---|---|---|
| Take over an existing request | `resume <url>` | The request is the only input: its source branch becomes the task, its target the base. Status CI_POLLING |
| The request does not target the base branch | `resume <url>` | Nothing special — its own target is stored, so the next `ship` updates that request |
| The request targets a branch that has since been deleted | `resume <url>` | Works. Only the next `ship` needs a target that still exists |
| Its source branch already belongs to a task | — | Refused: a task **is** its branch, so two cannot share one |
| Its source branch is not a legal task name (`feature/x`) | — | Refused with that branch named — the name becomes a directory and a tmux window too |
| The URL belongs to the configured code host | `resume <url>` | Branches and title read over that host's API — no model call |
| The request lives on a host jagt was never pointed at | `resume <url>` | The headless assistant follows the URL (paid) |
| That host's MCP server needs an interactive login | `resume <url>` | Refused as "not found": no headless session can authenticate one, so the read answers exists=false. The log names the source |
| The configured host claims the URL and the read fails | — | Refused, **not** retried through a paid read |
| Unreadable altogether | — | Refused. A guessed branch name would point the task at a branch the request does not track |

GitHub specifics:

| situation | what happens |
|---|---|
| A round is read (`code-host.type=github`) | Threads, the approval decision and the check rollup come from one GraphQL query — REST cannot say whether a thread is resolved |
| A reviewer wrote the request in the review body, no inline thread | Relayed all the same. A "changes requested" decision never comes back as "nothing to answer" |
| The repository requires no review, and someone approved | Counted as approved — the host reports no decision on an unprotected repo, so reviewer states are read instead |
| A ship opens the request | Squash and delete-branch-on-merge are not sent: they are repository settings, and jagt configures no repository |

## Review rounds

What the agent does with a comment:

| the comment | the agent |
|---|---|
| is right | Fixes it locally. Never commits or pushes on its own |
| is wrong | Changes nothing, replies with the one technical reason. Silent compliance is the failure this prevents |
| is unclear, or forces a design decision | Asks: REVIEW_PENDING with `outcome=question`. The board shows NEEDS INPUT |
| checks red, no comments | Fixes the build locally, then REVIEW_PENDING — it cannot push, so it never sees them go green |
| everything was already handled or pushed back on | REVIEW_PENDING with `outcome=no_changes`. Nothing is highlighted and no ship is advised |

| situation | what happens |
|---|---|
| An agent reports `no_changes` over files it edited | Not believed: one `git status` at report time, and the round is recorded as having a diff |
| The agent names its outcome in the message instead of the field | Read anyway — `outcome=question: …`, `no_changes: …`. Only behind the word `outcome`, since a false NEEDS INPUT is worse than a missed marker |
| Drafted replies exist | Both surfaces flag it; the push notification names the file. They are posted only after a human `ship` |
| The card says replies are drafted but there is nothing to post | Drafts count only while newer than the round now open. `replies` still prints the file, saying it was already sent |
| A thread the agent **fixed** | Resolved at ship time by the agent's own MCP — an unresolved thread is relayed by every later round |
| A thread it disagreed with or asked about | Left unresolved on purpose: resolving would read as agreement, and settling it is the reviewer's move |
| "Where are the other comments, and what will actually be posted?" | `replies <task>` — every block of `review_replies.md` on screen. On the board, the card's drafted-replies line opens it |
| `review_replies.md` is too long to read before shipping | The round brief prescribes the shape and is relayed every round, so a re-`sweep` re-briefs an agent that ignored it |
| An agent writes an essay in a request, a reply or a comment | It broke the brief's "How you write", which defers to the machine's own writing skill |
| The round came back clean, nobody approved | REVIEWED, and **nothing** is asked of the human: the owner is the code host. `deploy` stays listed for whoever needs no approval |
| An approval arrives | Lands unattended, and is the one thing the human is tapped for — it is now theirs to deploy |
| "Is this request approved?" | The dot beside the MR link: filled when it is, an empty ring while it waits, absent until a read has said |
| The pipeline goes red while the request is open | A red dot on the card, `CHECKS RED · …` on the console, and one notification the first time that run goes red |
| The dot is red while the request itself is mergeable | Read its tip — it prints the host's word verbatim |
| A big round with no `code-host` configured | Expect comments to go **missing**: the paid read returned 5 of 9 when measured. Configure the host before trusting a round |
| You type `review <task>` out of habit | It runs the sweep — the old spelling still resolves, and only the new one is advertised |

## Auto-review

| situation | what happens |
|---|---|
| The reviewer never resolves the threads | The poll keeps reading the request, but the brief is relayed only when the round actually changed |
| Comments arrive after the agent handed the round back | The next poll picks them up: polling follows the open request, not the status |
| A task goes back out for review on the same request | Every entry into CI_POLLING is a new round: the window restarts and the previous checks verdict is dropped |
| "Is anything actually polling?" | Both surfaces say so unasked: `auto-review on/off` in the header, and a `↻ 4m` countdown per task |
| Polling stopped and nothing is happening | The round outlived `autoReview.windowHours`. Both surfaces say `stopped polling this round after 24h; sweep it yourself` |
| A task has an open request and nothing polls it | The card names which reason: `off for this task`, or `cannot time this round (no stamp)`. Either way, `sweep` by hand |
| The expected poll has stopped | The card **does** ask for you — otherwise the task reads as waited-on while nothing looks at it |
| An install with `autoReview.enabled=false` | Not that case: it polls nothing by configuration, says so once per surface, and its cards stay as they were |
| A round answered every comment and changed no code | The card does not ask for you — `nothing to ship; the open threads are the reviewer's move`. It flips once polling stops |
| The MR link shows no age | Only a request written into `state.json` before any of this and never read since |
| "Is anything else running behind my back?" | The header carries the soonest run of any unattended job — and, outranking it, that a run threw. `jobs` is the detail |
| The jobs chip reads `due` and stays there | Right: a job run writes no state, and the ticker runs every 60s. Open the Jobs report for the real schedule |

## Deploy and revert

| situation | run | what happens |
|---|---|---|
| A request you have decided to land, whatever the review says | `deploy ABC-1` | Offered on any task with a request open, REVIEW_PENDING included. Git's only precondition is commits on the branch |
| "What exactly will this push?" | the Deploy button | The confirmation names one `project → branch` line per repository, and nothing else |
| "What will this revert take out?" | the Revert button | The branches it pushes to, and the scope: the last deploy only |
| Deploy | `deploy <task>` | Merges the task branch into `deployBranch` and pushes. Refused when that equals the base branch |
| Deploy hit a conflict | resolve in the deploy worktree | DEPLOY_CONFLICT; jagt keeps the half-done state for you |
| Take a deploy back out | `revert <task>` | Reverts the last recorded merge commit. Refused, with a by-hand recipe, whenever it would have to guess |
| The task was deployed more than once | `revert <task>` | Only the **last** deploy comes out. For the earlier rounds: `git log --merges --grep ABC-1`, then `git revert -m 1 <sha>` newest first |
| An agent is restarted on a task at REVERTED | `respawn` | Its reports are recorded but move nothing: the task stays REVERTED until a human ships or closes it |
| A task sits at REVERTED | `focus`, then `ship` or `done` | `deploy` is not offered — a revert adds a commit, so re-merging the same branch brings nothing |
| The confirm second-guesses your deploy | — | It does not any more. It names the writes and gets out of the way |

## Finishing

| situation | run | what happens |
|---|---|---|
| Ship a round | `ship <task>` | Commits, pushes the task branch, opens or updates the request. Never merges |
| The project versions a file jagt generates per worktree | `ship <task>` | The commit holds the task's work only; jagt unstages what it wrote for that worktree |
| Done | `done <task>` | Kills the agent window, reaps its language server, deletes the worktree. The branch survives |
| A card says "your move" instead of "action required" | — | The quiet tier: a good state whose next move is yours whenever. Not counted in the header, not kept by the filter |
| A deployed task still reads "action required" | — | It does not. DEPLOYED waits on nobody, like DONE; `done` is the only move left |
| Merged task branches pile up | your own git | jagt has no `prune` — cleanup is one task's own business |
| Someone types `prune all` anyway | — | Answered by name, before any model call: a retired verb must never be mapped onto a live one |
| "Is it me holding these up?" | `stats` | Per task: time on you, on its agent and on the code host, the rounds it has been out, and which of the three is slowest |
| The same numbers a week later | — | Not available. `done` removes the task, so `stats` describes open work, never throughput |
