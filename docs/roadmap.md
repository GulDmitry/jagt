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

Judging points go first. The finished-task record is what earns the right to touch an authorising one.

## Built, and where the rules for it live

- A verdict before a round reaches a human — `verifyCommand` per project, the VERIFYING status and `VerifyJob`
  ([`flow.md`](rules/flow.md)).
- The plan as an artifact with a gate — `plan.md` and PLAN_PENDING, whose move is yours.
- One record per finished task — `finished.jsonl` beside `state.json`, the status log kept whole, read by the
  `finished` report ([`components.md`](rules/components.md)).

## 1. The quality gate: a Master session running unattended

jagt already names this role: a session at the root carries no worktree header, so [every one
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
replaces you is that brief; the session is a process that reads it.

- Buys: the first reader of every diff stops being you.
- **jagt owns it**: a `Job` with no interval starts it at boot, one with an interval probes it the way
  `WatchdogService` probes a task — the session's own log, never a question put to it.
- **It reads every worktree, by design** — that is what reviewing is. The gate is not which worktree it may
  open, but that it writes to none.
- **Its spend is a line of its own**, booked against no task.
- **Three settings, not two**: off; on and judging; on and issuing verbs. The last one is the invariant at the
  top of this file. Off is the default, and no suite spawns one.

- **It does not replace `AutoReviewScheduler`**, it sits on top of one: whatever a light model can do stays with
  the light model, and reading a request and relaying its threads is that. The Master judges what comes back.
- **It stays off the grid** — the board is tasks in work. It is a report (a `GlobalCommand` with `report()`
  true), which the board picks up on its own, and one word in the header the way the jobs chip already works.
  A pseudo-card is what that replaces.

## 2. Work that arrives unasked, and verdicts that act

The two ends of the loop the playbook closes, both blocked on something outside this repository.

- **Off the tracker**: a ticket carries no project to route on and no label saying it is ready, and one written
  without the prompts a session works from produces work nobody asked for. Tickets written per project and per
  working prompt come first.
- **Off production**: a signal becomes a task. jagt holds no credential and reaches outside itself only through
  the one-shot assistant, so what jagt may promise before it holds a token is the decision, not the plumbing.
- **A verdict issuing a verb**: the invariant at the top, and the reason the finished record came first.
