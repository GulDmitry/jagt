# Roadmap

Where jagt grows, in what order, and what each step must not break. Loose ends nobody has scheduled stay in
the local `backlog/`; this file is the sequence and the reason.

## What growing means here

**Stability first** (owner 2026-11-24): short, controlled iterations, each released stable, fast and green before the
next starts; a long, unstable run is the defect to remove, never the pace to keep.

jagt is the loop from an accepted plan to a deploy. Every point in it that needs a human is already one list:
`Move.ownerOf` → `Owner.YOU`. Growing means taking points off that list, the cheapest reading first.

Two sorts of point, and the difference is the whole safety story:

- **Judging** — is this plan right, is this diff good, is this reply right. A model may hold one of these today:
  the outcome is still a human pressing the verb.
- **Authorising** — `ship`, `deploy`, `revert`, posting a reply. These are the human's, and the Master in `act`
  holds them because it stands where the human stands ([AGENTS.md](../AGENTS.md#the-human-in-the-loop)). The
  trigger stays deterministic either way; what changes is who the verdict belongs to, on a board that binds
  loopback without auth.

Judging points go first. The finished-task record is what earns the right to touch an authorising one.

### Where this ends

The list stops being what jagt happens to automate and becomes **configuration**: automatic is the default, and
an install names the steps it keeps — answering real people, pressing deploy, opening a task. Take the last one
off that list and the loop is closed.

Two kinds of flexibility, and only one is wanted:

- **How a step is done is pluggable** — a strategy behind a port, chosen by one value, a new one added as a
  class. Tracker or none, one code host or another, whatever a model can read over the MCP an install holds.
- **Who owns a step stays coarse.** Never a matrix of per-point toggles: a configuration able to leave a step
  ownerless is a bug nobody finds, and the fallback is always the human.

## Built, and where the rules for it live

- A verdict before a round reaches a human — `verifyCommand` per project, the VERIFYING status and `VerifyJob`
  ([`task-flow`](../openspec/specs/task-flow/spec.md)).
- The plan as an artifact with a gate — `plan.md` and PLAN_PENDING, whose move is yours.
- One record per finished task — `finished.jsonl` beside `state.json`, the status log kept whole and the human's waits, read by the
  `finished` report ([`components.md`](rules/components.md)).

### The quality gate: a Master session running unattended

A session at the root carries no worktree header, so [every one is Master](../AGENTS.md): it sees every task over the
same MCP, issues every verb, and writes no code; a trigger wakes it and a brief says what it judges.

**A fresh reader per round, one per role.** Each role of the brief reads the ticket and the diff on its own,
without the author's account or the other roles' reasoning, and the code sums them: a question or an unreadable
round to you, any finding back to the session, ready only where every role proved it. Reading is the cheap half
of the work, so it runs the heavier model.

**Deterministic where it counts.** The trigger stays a cadence, a status or an open request
([`review`](../openspec/specs/review/spec.md)) — only the judgement is the model's. And it cannot invent a move: the legal set
is `FlowRules.allowed` and `FlowEngine` refuses anything else with a sentence. **The flow table is the guardrail,
not the prompt.**

**Its memory is a file, not its context.** The standards are a brief in the install beside `jagt.yml`, pasted
into every run. What replaces you is that brief; each run is a process that reads it.

- Buys: the first reader of every diff stops being you.
- **It reads the task's worktrees and writes to none**: refused every write, commit and push tool.
- **Its spend is metered like any read**: each run is charged to its task and counted under
  `master review`, which the `master` report totals.
- **What its verdicts were worth is readable**: the verdict lands in the finished record at `done`,
  and `MasterRecord` counts the ways it and you can disagree — work it passed that never deployed or that you
  reverted, work it failed that you deployed anyway — and a question apart, since it judged nothing. The kept
  artifacts stay unparsed, so nothing here reads one.
- **Three settings**: off, judge, act. `act` is the default where the runtime reads the human's words; the plan approval
  and the deploy stay the human's (`roles.master.mine`, [0214](decisions/0214-the-master-acts-by-default.md)).
- **It does not replace `AutoReviewScheduler`**, it sits on top of one: whatever a light model can do stays with
  the light model, and reading a request and relaying its threads is that. The Master judges what comes back.
- **It stays off the grid** — the board is tasks in work. It is a report (a `GlobalCommand` with `report()`
  true), which the board picks up on its own, and one word in the header the way the jobs chip already works.
  A pseudo-card is what that replaces.

### Work that arrives unasked, and verdicts that act

- **Off the tracker**: `IntakeJob` takes the items a configured `TrackerWorkflow` says are ready, and
  `TicketCloseJob` closes a task whose item reached the stage that says the work landed. An item whose labels
  name no project is turned away rather than guessed at.
- **Off production** is not jagt's: a task starts from a tracker item or the human, and an alert reaches jagt
  only as an item filed outside it.
- **A verdict issuing a verb**: the Master in `act` presses every verb `roles.master.mine` leaves it, inside `FlowRules`.

## Where it goes next, in order

Each step lands green before the next starts; a task key names its backlog item.

1. **Decided behaviour restored**: a silence that ended green lands where the facts say (JAGT-395).
2. **The proof before the move** ([0269](decisions/0269-the-flow-is-proven-in-four-layers.md)): the simulation's missing fault
   events (JAGT-398 step 2); one e2e row per `StepPolicy` row, asserting today's stop names, the net over the Spring wiring (JAGT-399).
3. **Statuses land from facts** ([0268](decisions/0268-a-status-lands-from-the-level-of-the-facts-never-from-the-reads-event.md)):
   `job/ReconcileJob` is born here, running LAND alone (JAGT-400).
4. **One owner of progress** ([0260](decisions/0260-one-reconciler-owns-the-progress-of-every-in-flight-step.md)): RETRY,
   RESTART and STOP move into `ReconcileJob` one step kind per commit, `ProgressOwnerFitness` falling to 0 (JAGT-397);
   then a production monitor of the reducer's invariants on a write (JAGT-420).
5. **Behaviour is what a release and a refresh read**: a model plays the bad session against the real cycle (JAGT-403);
   the refresh and the audits read behaviour, not structure alone (JAGT-401).
6. **Borders written and measured**: one home naming what the orchestrator decides and what each role's model judges
   (JAGT-440, docs only, may start at once); each eval source set named for what it holds (JAGT-442); a judge's model
   family measured against the session's (JAGT-441); role-free read prompts out of the vendor's adapter (JAGT-345).
7. **Structure behind the move**: a task job's `run()` (JAGT-357), the relay mode (JAGT-354), task id and project as one
   value (JAGT-348), a session's signs as a port beside its launch (JAGT-346). Ratchets in no order: JAGT-293, 336, 337,
   343, 349, 353, 356, 359.
8. **The way to 1.0**: one installer or one image, setup in the app, roles as plug-ins, the CLI in the window, tested
   with no human; after jagt runs weeks stable.

No step may break: two doors; facts outrank claims; a deterministic trigger; the base branch read-only; the plan
approval and the deploy the human's ([0214](decisions/0214-the-master-acts-by-default.md)).
