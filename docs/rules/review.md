# Review rounds and unattended work

[← AGENTS.md](../../AGENTS.md)

## Review rounds

### Code review is never fully automated

The auto-review poll (`AutoReviewScheduler` → `ReviewSweepService`) only **reads and drafts**. An approval may
advance status, but comments are merely **relayed** to the agent, which fixes locally and writes its intended
answers to `review_replies.md`.

Nothing is pushed or posted without an explicit human `ship`. The loop never ships, deploys, pushes or posts on
its own. Every round hands the human two artifacts to inspect via `ide <alias>`: the local diff and the drafted
replies.

**Do not erode this: the human-in-the-loop gate lives in the outcome, not in who triggered the sweep.**

### A review round is a judgement, not a work order

Relay a bare list of comments and the agent implements all of them — including the ones wrong about the
architecture, which the reviewer could not see from the diff — and the human then reads agreement into code
that was only obedient.

`ReviewSweepService.brief` therefore opens with the three routes per comment: fix, change **nothing** and say
why, or ask via `outcome=question` before guessing. `sub-agent-context.md` carries the same stance for the task
itself.

A question **ends** the round (REVIEW_PENDING, `outcome=question`) rather than parking in CI_POLLING, because
the wait is the human's and the card has to say so. What keeps the agent from being re-briefed on the comments
it was told to hold is `AgentSessions.relayIfChanged`, not the status it left: a relay **nudges** the session,
so a brief the file already holds is an interruption to re-decide answered comments.

Deliberately **not** extended to jagt's orchestration steps: a commit or ship instruction **is** the human's
approval and is executed as given.

### A round reports its outcome as a field, not as a turn of phrase

All three outcomes end at REVIEW_PENDING and the human is advised from it, so `update_agent_status` takes
`outcome` (`question` | `no_changes` | `progress`) and `reviewRequestUrl`. The two structural facts stop being
scraped out of prose: the marker `flow/AgentReport` parses is written by **jagt**
(`AgentStatusReports.stated`), and the message is the human's sentence.

The prefix an agent typed itself is still read, as the **fallback**: a worktree keeps the brief it was created
with. `AgentReport` stays the one parser of the vocabulary (`Move` and `DashboardLine` both read it, so they
cannot disagree), and `Move` is total over (status × report).

**One of the three is checked rather than believed**: `no_changes` over a worktree holding uncommitted work is
recorded as a round **with** a diff (`WorktreeChanges` — one `git status` per report, never per render). That
claim is the one that suppresses the ship advice, and a hidden diff is what a human would then never read. A
question is not checkable, and no schema makes it so.

Advising SHIP for a no-change round is a **loop**: the ship commits nothing and starts another round on the
same unresolved threads. So NO_CHANGES highlights nothing and says the open threads are the reviewer's move.

### A reply does not resolve a thread

The sweep relays every **unresolved** one (`resolvable && !resolved`), so a comment the agent pushed back on
comes back every round forever.

The agent therefore resolves — at **ship** time, with its own MCP
(`ShipService.repliesStep`) — **only** the threads whose code it actually changed. A thread it disagreed with
or asked about stays unresolved: that disagreement is the reviewer's to settle, and resolving it would read as
agreement.

During the round the agent posts nothing at all — `review_replies.md` holds **drafts** until the human ships.

### The checks are read where the comments are, and shown without being asked for

A sweep already pulls the review round, so it stamps what the host said about the pipeline onto the task
(`TaskState.pipelineStatus`, the host's **own** wording), and `flow/Pipeline` is the one parser that turns it
into GREEN / RED / RUNNING / NONE — every host words it differently, and two surfaces matching on words would
agree by luck.

The board shows one dot in the card's meta row and the console prefixes the request line (`CHECKS RED · …`),
because a red run while the task still reads CI_POLLING is exactly what a status word cannot show.

The human is tapped **once** per run, on the transition **into** red: an unattended poll that notified every
time would be a loop, and a red run that is already known is not news.

### The reply file is a review artifact, so its shape is prescribed in one place

The round brief (`ReviewSweepService.brief`), which is relayed **every** round and therefore reaches sessions
whose worktree was briefed before the wording changed: one block per comment (thread, the quoted line,
`FIXED | NO CHANGE | QUESTION`, and the reply), with necessary-and-sufficient as the test on every line.

The human reads the whole file in one pass to approve a round, so a per-comment essay is work handed to them,
not thoroughness. The sub-agent brief keeps the **style** and points at the shape rather than restating it.

### Drafted replies are a file, not state

`TaskViews` stats `review_replies.md` in the worktree and puts a boolean on the projection — presence, not a
count, since a number is the host's claim about the round and not one a file read can make. Both surfaces
announce it, because a human who does not know the convention ships a round and posts replies they never read.

**The announcement is also where it is read** (the owner's rule, 2026-08-21): `replies [task]` is a report
(`command/ReviewRepliesReport`) that puts every comment, its verdict and the reply that will be posted for it on
the screen the human already has. The console line names the verb; on the board the drafted-replies line **is**
the button that opens it, and the board offers it **nowhere else** (`GlobalCommand.aboutOneTask`) — a bar
button pressed with no task named answers for all of them at once. Where a card announces nothing, because the
round was shipped or the status moved on, the verb is typed instead: `replies <task>` in Ask or in the console.
Approving a round by opening an editor in a worktree is a step nobody takes, which is how a round gets shipped
unread.

Two things it deliberately does: it reads the **file** rather than the card's badge (that one is announced only
where it is actionable, so a status that moved on would otherwise read as "nothing drafted"), and it prints
what does **not** fit the prescribed shape verbatim instead of dropping it — the file is agent-written, and a
parser that hid what it did not recognise would hide exactly the round that went wrong.

`GET /api/commands/{id}?about=<task>` is how a report narrows to one task: the same command the console types,
so no second endpoint.

**Presence is not enough: drafts belong to the round they were written in** (the owner's complaint, 2026-08-21
— a leftover file kept a task reading "action required" for a day). `ReviewDrafts.pending` is the one answer:
the file is there **and** newer than `mrCreatedAt`, because the ship that opened the round now open is the ship
that posted it.

**Who posts them decides that** (`CodeReviewConfig.shipPostsEveryDraft`): with `postReviewReplies=false`, or a
`reviewReplyAuthors` filter under which the agent posts some replies and deliberately keeps the rest, nothing
is ever spent — the answers are the human's to send, so the announcement stands until **they** end it.
`replies` still prints a spent file, since it is the only record of what was answered, but says it was already
sent rather than promising a ship will send it.

**jagt does not delete the file**, and that was decided against with a mechanism already written (2026-08-21).
A round stamp says a ship happened, never that the replies went out: posting is relayed to the agent and
deliberately off the critical path, so a dead session leaves "posted, not cleaned up" and "never posted" as the
same bytes on disk. Every deleting version also had to run *before* the ship it belongs to — the drafts of the
round being shipped are what that ship posts — which put it outside `ShipService`'s in-flight guard and in
front of every refusal, so a second click or a ship that then refused took the answers with it.

Not announcing a file is recoverable; unlinking it is not. The agent is still asked to delete what it posted
(the ship brief), and that stays a courtesy rather than the mechanism.

### One review sweep per task at a time

Whatever triggered it. The guard lives in `ReviewSweepService`, because the manual `sweep`, the auto-poll and
any future UI button all pass through it — two sweeps means the headless read paid twice and two briefs relayed
for one review round.

The other problem — ticks **queuing** behind a sweep that runs minutes — belongs to `Jobs`, which never runs a
job concurrently with itself, so `AutoReviewScheduler` keeps no guard of its own.

## Unattended work

### An open request is what the poller watches, never a status

The owner's rule, 2026-08-20. A reviewer writes on a request whatever the task is doing meanwhile, so
`AutoReviewCadence.polls` asks exactly two things: is there a request, and is the task still alive (DONE is the
one status that ends it — no worktree left to relay a round into).

Gating on CI_POLLING/REVIEWED meant a round the agent handed back stopped being read at all, and every comment
written after that — which is most of a bot's review — reached nobody until a human typed `sweep`.

The window is unchanged and stays the whole bound on polling: per round from `mrCreatedAt`, falling back to
`requestOpenedAt` so a request adopted by `resume` is polled instead of reading as untimeable.

Two consequences that are not optional: the relay is guarded (`relayIfChanged` — the same round read twice must
not interrupt the agent twice), and the poll may now find a task mid-work, which is fine because a sweep only
reads and drafts.

### Work that runs unattended must be visible while it waits

Not only after it acts. `AutoReviewCadence` is the **whole** auto-review policy — enabled, the interval ramp,
and `watch(task, now)` answering what a human is owed about one task (`task/AutoReviewWatch`: watching plus the
absolute next-poll stamp, window elapsed, off for this task, or nothing). `AutoReviewScheduler.decide` is a
translation of that same watch, so a card cannot promise a poll the scheduler will not make.

Both surfaces show it: the console's dashboard header carries `cadence.summary()` and each task a
`└ auto-review:` line; the board has the chip (`Board.autoReview`) and, per card, a `↻ <countdown>` **pulse** in
the meta row rather than a line of prose.

Whether polling runs at all is a property of the **install**, so it is stated once per surface and never
repeated per card — which is exactly why the words are the tooltip and only the countdown is on the card. A
watch that has **stopped** — window elapsed, or a task whose own `autoReview` is false while the install polls
— is that same slot wearing the **state** instead of a countdown, never a paragraph.

The countdown is an **absolute stamp** on the wire and formatted per surface (`DurationFormat.countdown` / the
page's own mirror), exactly as the two clocks on a card already are — a remaining-duration would be stale the
moment it was fetched. It is a **floor**, not a promise: the scan runs every 60s, so a poll shown as due
happens within the next tick.

### Unattended work is a declared kind, never a schedule a class keeps to itself

`job/Job` (id, one line of what the human gets, an interval or `null` for once at startup, `run()`) and
`job/Jobs`, the **one** ticker: each run on its own thread, never overlapping itself, a run that throws booked
against that job and nothing else — so no job needs a guard or a catch-all of its own.

A hidden `@Scheduled` cannot be listed, reported on or validated, which is the point. The `jobs` report (a
`GlobalCommand`, so both surfaces show it) names each job, its cadence, when it last ran and when it runs next:
**work nobody watches is visible before it acts, not only after.**

A report only answers whoever **opens** it, so `Jobs.summary` puts the two facts a human is owed unasked into
both headers: the soonest run of anything, and — outranking it, since the next run is not news while the last
one is broken — that a run threw. It is derived from the statuses already kept, never a second count.

An adapter's own workaround is a job **that adapter** contributes (the IDEA recent-projects cleanup), never a
permanent timer for everybody.

### What jagt did unattended is read back from its own log

`command/ActivityReport` tails `logging.file.name` (structured ECS JSON), keeps the entries that carry a `task`
key-value and renders them newest first for the `activity` verb and the board's Activity dialog.

The convention it depends on is the one already in force — **INFO for work nobody watched, nothing for a button
a human pressed** — so an in-memory ring buffer or a jagt-owned log file would be a second answer to "what
happened" **and** would not survive the restart after which a human looks.

It deliberately shows only work that named a task: `state.json` history already carries the status transitions
with who asked for them.

**One run, one log:** `surface/ui/SessionLog` empties the file and deletes the archives beside it before the
appender opens it, so the report is this session's work and nothing older — the owner's call (2026-08-18), and
the reason nothing gzipped is read back.

The file stays structured on **every** surface. `ConsoleLogging` used to try blanking
`logging.structured.format.file` for the console UIs, which would leave `activity` nothing to parse, and that
dead override is gone.
