# Roadmap

Where jagt grows, in what order, and what each step must not break. Loose ends nobody has scheduled stay in
[`TODO.md`](../TODO.md); this file is the sequence and the reason.

## What growing means here

jagt is the loop from an accepted plan to a deploy. Every point in it that needs a human is already one list:
`Move.ownerOf` → `Owner.YOU`. Growing means taking points off that list, the cheapest reading first.

Two sorts of point, and the difference is the whole safety story:

- **Judging** — is this plan right, is this diff good, is this reply right. A model may hold one of these today:
  the outcome is still a human pressing the verb.
- **Authorising** — `ship`, `deploy`, `revert`, posting a reply. [jagt acts on nothing by
  itself](../AGENTS.md#the-human-in-the-loop), and [a trigger is deterministic](rules/review.md). A setting that
  lets a verdict issue one of these makes both sentences false, on a board that binds loopback without auth.

Judging points go first. The record from step 4 is what earns the right to touch an authorising one.

## 1. A verdict before a round reaches a human

The brief tells the agent to report REVIEW_PENDING when the work is "done and verified", and jagt takes that on
its word — while it does **not** take `no_changes` on its word (one `git status` per report, `WorktreeChanges`).
The same distrust, applied to the build: a command per project in `jagt.yml`, run by jagt, on the report that
would end at REVIEW_PENDING. A red one is relayed the way a red pipeline already is (`ReviewFacts.pipelineFailure`
→ `<checks>`) and the round never reaches the human.

- Buys: nobody opens an IDE on a red tree. This is the step that raises how many tasks one person can carry.
- Costs a status: a suite of minutes cannot run inside a report, so it is a `Job`, and "being verified" is a
  state the board has to say.
- Must not break: the checks dot has one source today. A local verdict is a second source for the **same** dot,
  never a second dot ([`design.md`](rules/design.md)), and it is the open half of the stale-verdict TODO.

## 2. The quality gate: a Master session running unattended

jagt already has this role and already names it: a session at the root carries no worktree header, so [every one
is Master](../AGENTS.md) — it sees every task over the same MCP, issues every verb, and writes no code. What is
missing is not a kind of session. It is a way to wake one, and a brief saying what it judges.

**One session, not one per task.** It reads what a worker was given and what it handed back — the plan, the
diff, the drafted replies, the checks — and picks the move: relay, sweep, ship, or leave it for you. Reading is
the cheap half of the work, so it runs the heavier model.

**Deterministic where it counts.** The trigger stays a cadence, a status or an open request
([`review.md`](rules/review.md)) — only the judgement is the model's. And it cannot invent a move: the legal set
is `FlowRules.allowed` and `FlowEngine` refuses anything else with a sentence. **The flow table is the guardrail,
not the prompt.**

**Its memory is a file, not its context.** A session that lives for days gets compacted, and what goes first is
what was said earliest — exactly the standards it was started with. So the standards are re-read rather than
remembered: a brief in the install beside `jagt.yml`, and a fresh read of the artifacts per judgement. What
replaces you is that brief; the session is a process that reads it, and step 4 is what you grade it against.

- Buys: the first reader of every diff stops being you, and a verdict that can be compared with yours.
- **jagt owns it**: a `Job` with no interval starts it at boot, one with an interval probes it the way
  `WatchdogService` probes a task — the session's own log, never a question put to it.
- **It reads every worktree, by design** — that is what reviewing is. The gate is not which worktree it may
  open, but that it writes to none.
- **Its spend is a line of its own**, booked against no task.
- **Three settings, not two**: off; on and judging; on and issuing verbs. The last one is the invariant at the
  top of this file. Off is the default, and no suite spawns one.

Still open: whether the board carries it as a task or as a line of its own ([what a mark costs](rules/surfaces.md)),
and whether it replaces `AutoReviewScheduler` or is woken by it.

## 3. The plan as an artifact with a gate

`plan first` is `--permission-mode plan` and the plan lives in terminal scrollback: nothing reads it, nothing
versions it, nothing waits on it. Written to the worktree it becomes the cheapest artifact a human reviews — a
plan is minutes, a diff is not — and the first thing the reviewer of step 2 has to read.

- Buys: the human's attention moves to the artifact where changing your mind is still free.
- Costs a status between NEW and IN_PROGRESS whose move is yours, a row in `FlowRules`, a `Phase`, a legend row.
- Must not break: approving a plan is a relay, not a new verb, unless it turns out to be more than one
  ([`surfaces.md`](rules/surfaces.md)).

## 4. One record per finished task

`done` deletes the briefing, the standing instruction and the drafted replies, and with no ticket behind it the
words that started the task go too. `stats` therefore describes open work and can never be asked for throughput.
One record per finished task, held whether or not anything reads it yet: status stamps, rounds, verdicts, spend,
and where the reviewer of step 2 disagreed with you.

- Buys: the numbers that decide whether step 5 is safe — and every later thing built on finished work.
- Open: [where it lives](../TODO.md), given that the base branch is read-only.

## 5. Work that arrives unasked, and verdicts that act

The two ends of the loop the playbook closes, both blocked on something outside this repository.

- **Off the tracker**: a ticket carries no project to route on and no label saying it is ready, and one written
  without the prompts a session works from produces work nobody asked for. Tickets written per project and per
  working prompt come first.
- **Off production**: a signal becomes a task. jagt holds no credential and reaches outside itself only through
  the one-shot assistant, so what jagt may promise before it holds a token is the decision, not the plumbing.
- **A verdict issuing a verb**: the invariant at the top of this file, and the reason step 4 comes before it.
