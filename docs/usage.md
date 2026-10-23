# Usage

[← README](../README.md)

jagt has one surface: the **board** at `http://localhost:8290`.

## The board

Every task is a **row** of two lines, the tasks waiting on you in a band on top and the rest by project, each in
the order it was registered: a row moves only when whose move it is changes. One line of counts above the list
holds the pipeline (build → review → check → ready → deploy → done), and `order: added` switches to alias order.
`▸_` on a row is Focus without opening the task. The empty board offers **New task** and **Add a project**.

**The open task** stands beside the list: its card with every legal action, the obvious one highlighted and each
one's key drawn on it, `Done` apart at the end; then four tabs: **Overview** (the status history), **Session** (its screen,
with a line to tell it), **Drafted review** (the drafted replies) and **Files** (`plan.md`, `task_notes.md` and the other
documents of the task, rendered). `deploy`, `revert` and `done` ask for
confirmation. Under 1200 px the open task replaces the list; `Esc` goes back.

**Keys.** `j`/`k` walk the list, `↵` opens the picked row, `n` opens the next task waiting on you, `f` focuses the
open task, `t` opens New task, `/` the filter, ⌘K a command, and a verb's own letter runs it on the open task. The
tab's title counts what waits on you, and its icon wears a dot — red when something broke.

**The header** says only what needs a look: how many tasks wait on you, a failed job (press it for the jobs, each
runnable now), the Master while it presses your buttons, review polling when it is off. **Reports** holds every
report; **New task** holds the form, with **+ add a project**, which reads a repository path and writes its entry
into `jagt.yml`.

**Filtering.** Type in the filter box (`/` focuses it, `Esc` clears) to match an alias, ticket number or title. The
page never polls; the backend pushes changes, and a tab left open across a restart loads the new board itself.

**Focus** selects the agent's tmux window in kitty and raises it; **Shift+←/→** switches between them there.
Closing the viewer only detaches it — agents live in tmux and keep working; `done` kills them. Every task also
gets a short alias (`p1`, `s2`) usable anywhere instead of the ticket id.

## Commands

### Per task

| command | what it does |
|---------|--------------|
| `do <ticket> [plan] [notes]` | read the ticket, cut a worktree, launch an agent. `plan` = the session plans first; `approve-plan <ticket>` starts it on the plan |
| `do <projA>[,<projB>] <what to do…>` | no ticket: your words are the task and name its branch |
| `do <ticket> from <branch>` | cut from `<branch>` and target it in the request — for stacking on a feature branch |
| `do <ticket> <projA>,<projB>` | one task, one agent, a worktree per repository |
| `do <ticket> recreate` / `resume` | the branch already exists: cut it fresh, or take over its commits |
| `focus <ticket>` | jump to the agent's session and talk to it directly |
| `ide <ticket>` | open the worktree as a project — Git → Local Changes is the live diff |
| `ide <ticket> diff` | a static snapshot against the branch the request targets |
| `diff <ticket>` | every repository's diff against the branch its request targets |
| `ship <ticket>` | commit, push, open or update the review request |
| `sweep <ticket>` | pull checks + comments; the agent fixes locally and drafts replies |
| `replies [ticket]` | every comment of the round with its verdict and the reply drafted for it |
| `resume <request-url>` | adopt an existing review request: its branch, its commits, its target |
| `deploy <ticket>` | merge the task branch into `deployBranch` and push |
| `revert <ticket>` | undo that deploy with a revert commit |
| `respawn <ticket>` | restart a dead agent session |
| `add-repo <ticket>` | attach the repository a session asked for: its worktree is cut and the session told |
| `trust <ticket>` | adopt the git config or hooks jagt found changed |
| `done <ticket>` | close the task: session, worktree and state gone (the branch stays) |

### Global

| command | what it does |
|---------|--------------|
| `stats` | subscription limits, jagt's own model spend per task, cycle time |
| `activity` | what jagt did unattended |
| `jobs` | every scheduled job, its cadence, last and next run |
| `run <job>` | run an unattended job now instead of at its next tick |
| `finished` | what has been closed, how long each took, how many rounds it cost and who held it |
| `master` | the unattended reviewer: what it may do, and what it judges by |
| `act` | give the Master back the buttons it stopped pressing after three bad endings in a row |
| `help` | command reference and recovery cheatsheet |
| *anything else* | free text — see below |

Every report is plain text over HTTP any time:

```sh
curl -s localhost:8290/api/commands/stats
curl -s localhost:8290/api/commands/activity
```

### Free text

Type a sentence instead of a command (the `⌘K` button, or its key) and a model maps it onto **exactly
one** of the commands above, runs it through the same gate a button uses, and says what it understood. The
palette completes the grammar as you type and says whether the line will run (green) or why it will not
(`no task "a9"`). **A line that parses is executed as typed, with no model call**; one mistyped word is
treated as a typo, not as prose.

## The review loop

`do → ide → ship → sweep → ide → ship` repeats once per review round until CI is green and every thread is
resolved, then `deploy`, then `done`. `focus` is available throughout — the agent is a session you can talk
to — and after a deploy you may ship and deploy again as often as you like. jagt refuses a move that makes no
sense for the task's status, with a sentence rather than a git error.

## The Master

A reader between the session and you, on by default.

| `master.mode` | what it does | costs |
|---------------|--------------|-------|
| `off` | nothing: every plan, round and question waits for you | nothing |
| `judge` | reads each plan, then each round by its brief's roles; writes `master-review.md`, sends findings back, presses no verb | one heavy read per plan; per role per round |
| `act` | also starts a holding plan, ships a ready round, answers a question, opens split-off work, deploys a green request; closing stays yours | the same |

It judges by the shipped brief; to edit one, copy `master-brief.md.dist` and set `master.brief`.
`master.mine` keeps named steps yours in `act`, `[plan]` unset; what you type to a session overrules it.
`revert` and `sweep` stay yours in every mode.
Each step is a line in the `master` window.

## Notes on the tricky ones

**`ship`** is the agent's own work, with its own code-host tools: commit (title from `mrTitlePattern`), push
the task branch, open or update one request **per repository**, post the drafted replies.

**`sweep`** only **reads and drafts**: the agent fixes locally and writes its intended answers to
`review_replies.md`; nothing is pushed or posted until you `ship`. Read them with `replies` first.

**A review round is a judgement, not a work order.** The agent may fix a comment, change nothing and say why,
or ask you; it does not implement a suggestion it thinks is wrong.

**`resume`** takes over an existing review request: its branch comes back with its commits, its target is
remembered, and the next `ship` updates that request.

**`deploy` on conflict** pushes nothing: the task goes `DEPLOY_CONFLICT` and `ide <ticket>` opens the **deploy**
worktree — resolve, `git add`, `deploy` again. Your task branch and request are untouched.

**`revert`** reverts the merge commit `deploy` created and pushes it — it only *adds* a commit, so your branch
survives and the normal follow-up is fix and `ship` again. It refuses, writing nothing, when the commit is
already reverted, is not on the branch, or the revert conflicts.

**Multi-repo tasks.** One task, one agent session, a worktree per repository (the session runs in the first one
named). `ship` opens a request per repository; `sweep` reports them as one round, as far along as the least
finished one.
