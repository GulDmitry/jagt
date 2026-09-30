# Use cases

**One line per situation**, from your side: what you see, what you do, what happens. How it works lives in
[`docs/rules/`](docs/rules/), the rules in [`AGENTS.md`](AGENTS.md), the map in [`ARCHITECTURE.md`](ARCHITECTURE.md).

- [Starting jagt](#starting-jagt)
- [Starting work](#starting-work)
- [Multi-repo tasks](#multi-repo-tasks)
- [Reading a ticket](#reading-a-ticket)
- [Watching an agent](#watching-an-agent)
- [Reading the board](#reading-the-board)
- [Review requests](#review-requests)
- [Review rounds](#review-rounds)
- [Auto-review](#auto-review)
- [The Master session](#the-master-session)
- [Deploy and revert](#deploy-and-revert)
- [Finishing](#finishing)

## Starting jagt

| situation | run | what happens |
|---|---|---|
| Something the setup needs is missing or wrong | start jagt | Refused with **every** problem at once, each naming the key that fixes it |
| A token is wrong or the host unreachable | start jagt | Not detected: a start checks nothing over the network |
| A suite boots on a machine with no desktop | `--orchestrator.startup-checks=false` | Skips every check |
| "Where does a setting go?" | — | `jagt.yml` at the repository root; copy `jagt.yml.dist`, where every key is described |
| A key you set had no effect | — | A command-line flag outranks the file; `projects` is re-read live, everything else needs a restart |
| jagt refuses to start over `config.json` | — | It is no longer read; the refusal prints the `jagt.yml` to write instead |
| jagt on Linux with the platform left unset | start jagt | Refused: unset means macOS |
| You work **on** jagt with another agent CLI | open the repository | Same rules, same MCP server: `AGENTS.md` is what every CLI reads |

## Starting work

| situation | run | what happens |
|---|---|---|
| New ticket | `do ABC-42` | A branch `ABC-42` cut from the project's base, its own worktree, an agent in it |
| The project is not obvious from the ticket | `do ABC-42 <project>` | Same, without guessing the project |
| Work with no ticket behind it | `do <project> <what to do…>` | Your words are the instructions and name the branch |
| You want the plan before the code | `plan first` | The card reads `plan waiting`; your next instruction to the session approves it |
| Work must start from another branch | `do ABC-42 from <branch>` | Cut from it, and the request targets it; deploy still goes to `deployBranch` |
| The branch already exists | `recreate` or `resume` | Nothing is freed or moved until you pick one |
| The base repository has the branch checked out | nothing | Freed and detached in place, with a warning naming the branch |
| Uncommitted tracked changes there, or another worktree holds the branch | commit, stash, or free it | Refused naming the directory; untracked files block nothing |
| The app needs a gitignored `.env`, key or cert | `worktree.copyGlobs` | Copied into every new worktree at the same path |
| The repository has its own `CLAUDE.md`, `AGENTS.md`, Codex config or git hooks | `do ABC-42` | All left untouched and still in force; jagt's own briefing and guard sit beside them |
| The agent pushes a branch that is not the task's | nothing | Refused before anything leaves the machine |
| On the board | the launch row | Ticket, project, base branch, `plan first`, notes, branch strategy, Start |

## Multi-repo tasks

| situation | run | what happens |
|---|---|---|
| Two repositories that move independently | two tasks | Every verb is per task |
| One change across two repositories | `do ABC-42 api,web` | One task, one session, a worktree per repository |
| It reaches review | `ship ABC-42` | A commit, a push and a request per repository, each onto its own base |
| One of them needed no change | `ship ABC-42` | Passed over and named; it gets no empty request |
| A round comes back | `sweep ABC-42` | The task is as far as its least finished repository: approved only when all are |
| It is ready to deploy | `deploy ABC-42` | Repository by repository, in the task's order; one with nothing to deploy is passed over |
| One conflicts after another landed | `deploy ABC-42` | DEPLOY_CONFLICT naming both sides; the next `deploy` resumes there |
| The deploy breaks off for something no worktree fixes | — | Status untouched; the message names what is already live |
| Taking it back out | `revert ABC-42` | Reverse order, only what landed |
| `deploy` keeps refusing over the deploy directory | — | The message names what is in the way; a sibling repository's worktree is refused by name |
| On the card | — | One `<project> MR` link per repository, one age for the whole task |

## Reading a ticket

| situation | run | what happens |
|---|---|---|
| The ref is a key or a URL | `do ABC-42`, `do <url>` | Title, labels and project read through your own MCP servers (paid) |
| No MCP server reaches the tracker | `do ABC-42 <project>` | The read fails naming what stopped it; the task carries no title |
| The read says "does not exist" for a ticket that plainly does | — | Asked again, up to three times, each attempt told what the last got wrong |
| The read answers about a different key, or with no key or link | — | No task, and the message says why |
| The item has no summary | — | A short title is written from the description; a link is never invented |
| An item reaches your start stage | nothing | The task opens itself (`tracker`, off by default) |
| An item reaches your landed stage | nothing | The task closes itself, its work having left the worktree |
| A paid read must not depend on today's MCP servers | `assistant.mcp-config` | Only the declared servers load: steadier, and costs **more** |

> [!NOTE]
> `assistant.mcp-config` takes a path or the JSON itself. Declared servers have no plugin prefix in their tool
> names, so rewrite `allowed-tools` if it was set.

## Watching an agent

| situation | run | what happens |
|---|---|---|
| An agent stops to ask | — | NEEDS INPUT on the card, and one desktop ping the first time |
| The task contradicts what the code guarantees | — | The agent asks before the code picks a side; "the ticket wins" is yours to say |
| The agent settled something without asking | `focus <task>` | An `OPEN QUESTIONS:` line ends its terminal output |
| An agent ends its turn without reporting | — | Claude is sent back once to report first; after that, the row below |
| An agent stops and never says so | — | The card turns over: NEEDS YOU, Focus highlighted |
| A session sits at a permission prompt | — | Reported within seconds |
| A turn ends and the card says nothing | — | Correct: a turn end is silent until nothing has moved for a while |
| A card sits at `verifying` | — | jagt runs the project's `verifyCommand`; a red run goes back to the session, not to you |
| The agent CLI never came up | `focus <task>` | The card says so: at NEW the launch is what to look at |
| You want to talk to the agent | `focus <task>` | Its window is selected and the viewer raised; if it cannot be, `focus` says why |
| The viewer was closed by mistake | Focus | Nothing stopped: the agent lives in tmux; Focus opens another viewer |
| You want the board from a second machine | `--server.address=0.0.0.0` | Off by default: the board has no password, and it can deploy |
| The backend restarts while sessions are live | — | Nothing to do: the next call reaches the new process |

> [!IMPORTANT]
> A worktree is briefed once, at creation. One created before a brief changed keeps the old wording — answer in
> the window, or recreate the task.

## Reading the board

| situation | what it means |
|---|---|
| "Where is my task?" | Where it was: cards keep their order; only `order: alias` lets a new task take a retired one's place |
| Finding one task among many | The filter box (`/`): alias, ticket number or title; `Esc` clears |
| "It said 17h, I restarted the agent, now 0m" | The clock is time in **that** status, and a fresh session reports itself anew |
| "How long has this request been hanging?" | The `MR 8h` chip: the request's own age, surviving rounds and restarts |
| "Has anyone approved?" | The chip: green with **✓** once approved |
| "Did the checks pass?" | The dot beside it: red failed, green passed, a pulsing ring still running, none not read yet |
| "When is the next poll?" | In the tooltip; a poll that has **stopped** gets its own mark |
| Where the ticket and request links are | The task number opens the ticket, the `MR` chip the request |
| The line under a card | News only: NEEDS INPUT, ANSWERED, PROBLEM, NEEDS YOU |
| "What does this status mean?" | It says itself in words; the highlighted button says what to do |
| Why only some cards carry a badge | The badge is **your** move; a quiet "you can …" is a move that can wait |
| A deployed task wears no badge | Correct: it waits on nobody, and `done` is the only move left |
| "Which buttons change something?" | The top row moves the task on; the bottom row only looks or restarts |
| "What does this colour or ring mean?" | `Help`, above the commands: every mark beside its meaning; one meaning each, board-wide |
| "Has this been deployed already?" | The Deploy button is green while the work is live; `revert` takes the colour off |
| The project key is missing from a card | The install has one project |
| Clicking a desktop notification | Opens the board filtered to that task; on Linux the task is in the title instead |
| "Is it me holding these up?" | `stats`: per task, time on you, the agent and the code host |
| "Can I trust the Master?" | `master`: what it judged, and where you did otherwise — passed then never deployed or reverted, failed then deployed |
| The same numbers a week later | `finished`: every retired task, whole status log included |

## Review requests

| situation | run | what happens |
|---|---|---|
| Take over an existing request | `resume <url>` | Its source branch becomes the task, its target the base; conflicts left for the session |
| Its branch already belongs to a task | — | Refused: two tasks cannot share a branch |
| Its branch follows another convention (`feature/x`) | `resume <url>` | Taken over as-is |
| Its target branch was deleted since | `resume <url>` | Works; only the next `ship` needs a target |
| No working MCP server for that host | `resume <url>` | Refused as **unread**, never as missing, naming which servers are down |
| The host answers there is no such request | `resume <url>` | Refused in those words: the one case "does not exist" is said |
| GitHub: a review written in the body, not a thread | — | Relayed all the same |
| GitHub: no review required, and someone approved | — | Counted as approved |

## Review rounds

What the agent does with a comment:

| the comment | the agent |
|---|---|
| is right | Fixes it, uncommitted; never pushes on its own |
| is wrong | Changes nothing, replies with the one technical reason |
| is unclear, or forces a design decision | Asks: NEEDS INPUT on the board |
| contradicts what the code enforces | Asks before writing the code that picks a side |
| checks red, no comments | Fixes the build and hands the round back |
| was already handled | Says so; nothing is highlighted and no ship is advised |

| situation | what happens |
|---|---|
| The agent says it changed nothing, but files changed | Not believed: the round counts as having a diff |
| "What will actually be posted?" | `replies <task>`, or the card's drafted-replies line: every drafted reply on screen |
| A drafted reply is wrong | Say so under the open `replies` report; the session answers into the same report |
| Replies are posted | Only after your `ship`; a fixed thread is resolved, a disputed one left for the reviewer |
| The replies are too long, or an essay | The agent broke its brief; the brief is relayed every round, so a re-`sweep` re-briefs |
| The round came back clean, nobody approved | REVIEWED, and nothing is asked of you |
| An approval arrives | The one thing you are tapped for: it is yours to deploy |
| The pipeline goes red | A red dot, and one notification the first time that run goes red |
| You type `review <task>` | It runs the sweep |

## Auto-review

| situation | what happens |
|---|---|
| "Is anything polling?" | The header says `auto-review on` or `off` |
| Comments arrive after the agent handed back | The next poll picks them up: polling follows the open request, not the status |
| The reviewer never resolves the threads | Polls continue; the agent is re-briefed only when the round changed |
| A poll cannot read the round | Retried, then **one** desktop ping naming what stopped it |
| Polling stopped | The round outlived `autoReview.windowHours`: the card says `polling stopped` and asks for you |
| An open request nothing polls | The card says `polling off` or `cannot time this`: `sweep` by hand |
| A deployed task comes back green and unapproved | It stays `deployed`: a poll never moves work that went out |
| A round answered every comment and changed no code | Not your move: the open threads are the reviewer's |
| No checks dot while the host shows a failed run | Nobody has read one yet; `none` means the host listed no pipeline |
| "Is anything else running behind my back?" | The header shows the next unattended job, and that one failed; `jobs` has the detail |

## The Master session

| situation | run | what happens |
|---|---|---|
| You want a round read before you look | `master.mode: judge` | The Master reads each handed-back round and writes `master-review.md` in the worktree; it presses nothing |
| You want it to act for you | `master.mode: act` | A `ready` round is shipped for you; `master.mine` keeps steps yours, `done` always is |
| No brief configured | start jagt | Refused: it judges by `master.brief`, copied from `master-brief.md.dist` |
| The Master is reading a round | — | The chip says `master review`, nothing asks you; the session was told to end its turn |
| Whose roles a round is read by | — | The session's own `<self_review>`, before it hands back; the Master reads by the same, unless its brief names its own table |
| A session stops to ask | `master.mode: act` | The Master answers it in the session's window and the task goes back to work; `mine: [answer]` keeps it yours |
| A round is not ready | — | Its findings go back to the session that wrote the code, in any mode, and the task to `IN_PROGRESS`: the fix is a new round |
| It writes `ready` but its file still lists findings | — | Counted as not ready: the findings go back to the session |
| It is unsure what the ticket asks ("switch" or "add beside") | — | In `act` it decides as you would and sends the decision back as a finding; a question reaches you in `judge`, or where `mine` keeps `answer` |
| A round removes something that exists (an endpoint, a version) and no ticket line asks for it | — | A question, not a verdict |
| The ticket's acceptance check was never run | — | Not ready, or a question where it cannot be run |
| What it reads | — | The ticket and the diff; a premise its verdict rests on is proven by a run, never taken from the author |
| What a round costs | — | One heavy read per role of the brief, charged to the task; `master` shows the total |

## Deploy and revert

| situation | run | what happens |
|---|---|---|
| Deploy | `deploy <task>` | Merges the task branch into `deployBranch` and pushes; offered whenever a request is open |
| "What exactly will this push?" | the Deploy button | One `project → branch` line per repository, and nothing else |
| "What will revert take out?" | the Revert button | The branches it pushes to; the last deploy only |
| A conflict | resolve it there (`git add`), then `deploy` | DEPLOY_CONFLICT until you do |
| Take a deploy back out | `revert <task>` | Reverts the last deploy's merge; refused, with a by-hand recipe, where it would have to guess |
| It was deployed more than once | `revert <task>` | Only the last comes out; earlier ones by hand: `git log --merges --grep ABC-42`, `git revert -m 1 <sha>` |
| A task at REVERTED | `focus`, then `ship` or `done` | `deploy` is not offered: re-merging the same branch brings nothing; the agent's reports move nothing |
| A shipped task needs one more change | instruct the session | Back uncommitted for review; only a new `ship` lands it |
| A pushed commit turns out wrong | — | Another commit, never a rewrite: no force-push |

## Finishing

| situation | run | what happens |
|---|---|---|
| Ship a round | `ship <task>` | Commits, pushes the task branch, opens or updates the request; never merges |
| The project versions a file jagt writes per worktree | `ship <task>` | The commit holds the task's work only |
| A diff shows files nobody on the task touched | `ide <task> diff` | It is read against the request's target, never `deployBranch` |
| Done | `done <task>` | Ends the agent, deletes the worktree and every checkout the task cut; the branch survives |
| `jagt-diff-*` worktrees in the temp directory | `done <task>` | The board's diffs made them; retiring the task ends them |
| Someone types `prune all` | — | Refused by name: no bulk cleanup |
