# Whose move it is

[← AGENTS.md](../../AGENTS.md)

## Whose move it is

### A blocked session is on the dashboard, whatever blocked it

The owner's rule, 2026-08-19. It has two halves, because a stopped agent may or may not manage to say so.

**The agent's own half** is `outcome=question` *before* it puts any question to a human — rule 1 of
`sub-agent-context.md`, and the `update_agent_status` tool description says it too (a worktree is briefed once,
while a tool description reaches every session). `AgentReport.QUESTION` flips `Move.owner` to YOU from whatever
status it kept, `DashboardLine` reads NEEDS INPUT, and `AgentStatusReports` pings once, on the transition
**into** asking.

**The half no prompt can promise** is the agent that never got the chance — a token limit, a crash, a
permission prompt nobody answered. So `WatchdogService` **stamps** what `service/SessionProbe` finds
(`TaskState.silentSince`) instead of only sending a ping a human dismisses, and the same owner flip plus a
NEEDS YOU line happen with the agent saying nothing.

**Terminal output cannot answer whether a session is waiting**, and that is measured rather than assumed: a
Claude window waiting on a question repaints every 10–30s (2026-08-20), so a window-activity probe stays warm
forever and no stamp is ever written. Two signs that do carry that half, neither costing a token:

- **the CLI's own hooks**, which say so within seconds because the harness fires them rather than the model —
  a session out of tokens still reports, and `WatchdogService.check` runs that one task at once rather than
  letting it wait out the interval. Kept in memory: losing them on a restart costs promptness only.
- **the log a session keeps of itself** (`AgentRuntime.lastSessionActivityMillis`, or the path a session
  reported), which grows only when something happened.

Terminal output survives as the **last** sign rather than the first, and that is not a hedge: a log gets its
entry when a tool call is issued and nothing while it runs, so an eight-minute build reads as death without
it. What a prompt looks like is the harness's to report, never this threshold's to infer.

**A hook reports; it decides nothing.** The stamp stays the watchdog's single verdict, or one surface would
start calling a session blocked while the other has it working. A session reporting itself **alive** drops what
was said before rather than outranking it by time: the two hooks of one restart are stamped on arrival and can
share a millisecond.

**Why it stopped is stamped beside when** (`TaskState.silentBecause`), because a session that ENDED and one
waiting for a keypress need different moves, and a timestamp cannot tell them apart. Both surfaces read the one
sentence `DashboardLine` builds from it — and at NEW it is overruled, since nothing has reported at all yet and
the launch is what a human should be looking at.

Three things hold it up:

- Every status whose `Move.ownerOf` is AGENT is watched by the watchdog, pinned in `WatchdogServiceTest`. A
  status the agent owns and nothing watches is a session that waits forever.
- The stamp is written only on the **transition** in or out of silence, because both surfaces repaint on a
  state write and that job runs every minute.
- **Any** report clears it (`withStatus`), so a task that answers stops asking.

Neither surface needed a new field for it — owner, hint and detail already carry it, which is what parity
means here.

### `Phase` / `Owner` are a projection, never a second state machine

`TaskStatus` stays the SSOT; neither is persisted. Eleven statuses collapse into six phases because four of
them read as the one word "review".

**Liveness is deliberately not an input to the projection** (it would cost a tmux probe per task per render).
A task stuck at SHIPPING is therefore offered SHIP, and the gate refuses at execution time if its agent is
alive.

### `Owner.YOU` means an action of theirs exists

A status alone cannot always say so. Whether a card wears a badge at all, the "need your action" count and the
own-move filter all read the tier **below**, which is NONE exactly when the owner is not YOU — so a card that
asks for a human who has nothing to do teaches them to ignore all three.

Two cells are decided by more than the status (`Move.ownerOf`):

- A REVIEW_PENDING round that changed nothing and drafted no reply waits on the **reviewer** — the only move
  left is a ship jagt itself advises against.
- A round whose expected poll has **stopped** waits on the human. That is `AutoReviewWatch.stopped()`, not "is
  not being polled": an install that polls nothing at all says so once per surface, and flipping every card in
  it to "action required" would be the same unread badge in the other direction.

The projection reads it off the very watch the card renders, so the badge and the countdown cannot disagree. A
caller that only wants the sentence keeps the four-argument `forTask`.

### Work handed in and waiting for a reviewer is not an action required

The owner's rule, 2026-08-20. REVIEWED means "nothing unresolved, checks green, **not** approved" — the status
*before* an approval. Its owner is the **review request**, nothing is highlighted on the card, and no desktop ping
goes out. `deploy` stays in the action list for an install that needs no approval, because gating it was
already decided against.

Three things follow, none optional:

- **APPROVED is the one the human is tapped for.** `AgentStatusReports.ping` asks `Move` whose move it is and
  sends nothing unless the answer is YOU — a second list of statuses worth interrupting for would drift from
  the badge.
- **A REVIEWED round is still polled**, as is every other status: `AutoReviewCadence.polls` asks only whether a
  request is open, because an approval arrives after the round came back clean.
- **Where nothing is polling for the approval, the read is what the card highlights.** That is why
  `Move.forTask` takes the `AutoReviewWatch` and not a flag off it: "the poll this round was promised has
  stopped" and "nothing is polling at all" are different questions.

### An action that can wait is not an interruption

The owner's rule, 2026-08-21. The human's own turn has two tiers, and `flow/Attention` is the one value that
says which:

| tier | means | gets |
|------|-------|------|
| `REQUIRED` | a session that stopped or asked, a round back from review, a red run, a conflict, a round nothing will read again | the header count, the own-move filter, the alarm edge |
| `OPTIONAL` | a good state whose next move is theirs whenever — today an approval that landed, and a revert they made themselves | a card-level chip in the flat chip colour, nothing else |
| `NONE` | exactly when the owner is not YOU | nothing |

Pinned over every status in `MoveTest`, because a card counted in "N need your action" while its badge says
the move can wait is the drift that makes the badge worth nothing.

### The badge names the act, never the tier

The owner's rule, 2026-08-25. "action required" said that something was theirs and nothing about what — the
same two words over a session waiting on an answer, a red run and a deploy conflict, so the card had to be
opened before it said anything. `Move.ask` (`ask` on the wire) names the act instead, in the words a chip has
room for.

It never names an act the card cannot serve, which is what keeps it from becoming a second, drifting copy of
`Move.hint` — the same act at sentence length, which the badge carries as its tooltip. Usually that means the
highlighted verb. Where the act is **reading**, it is the card: its diff, its drafted replies and its session
are each one click away, and no one verb is the whole of it.

The tier keeps the loudness and loses the words: it is what the header count, the own-move filter and the
card's colour read. `MoveTest` pins `ask` non-null over exactly the statuses whose tier is not `NONE` — with a
request and without, polled and elapsed, since those are the only routes by which a round out for review ever
becomes the human's.

**The quiet tier says so in grammar, not in colour.** An interruption is an imperative and a move they can make
whenever is offered — `resolve the conflict` against `you can deploy it` — because a badge that only a shade
tells apart is one a human on a monochrome terminal, or with the commoner kind of colour blindness, cannot tell
apart at all.

The console prints the tier word rather than the act: it has no colour to spend on one, and the sentence beside
it is already the specific answer.

**The ping still reads the owner rather than the tier** — an approval landing is an event worth a banner even
though it can wait.

### Neither is work that is already live

The owner's rule, 2026-08-21. DEPLOYED's owner is **nobody**, exactly as DONE's is. Closing the task is
housekeeping a human does when they feel like it — `done` stays the highlighted move and nothing else is left.
A badge there sat next to a stalled session wearing the same colour and the same word, which is precisely how
a human is taught that "action required" means nothing.

A question asked from DEPLOYED still flips it: a stopped session **is** their move.

### A question outranks the status it was asked from

In all four places that read a round: `Move.ownerOf` (the wait is the human's), `Move.primaryOf` (FOCUS — the
status alone would highlight a verb that *acts*, and a SHIP on a round the agent said it cannot finish is the
worst button on the board), `Move.hint` and `DashboardLine`.

**A question that came back WITH its round is read first, not answered first** (the owner's rule, 2026-08-25).
The session is blocked either way — that is why the primary stays FOCUS, which is where the answer goes. What
differs is the human's first act: at REVIEW_PENDING the round is finished and on the card, so the badge says
`review the round` and the hint names `focus` after it. Asked from any other status there is no round to read
and `answer the session` is the whole of the move.

**And a question on a round a poll is still reading is not an alarm** (the owner's rule, 2026-08-25). Comments
keep arriving on an open request from people who are not the one being shouted at, and the next of them may be
the answer — so a question at REVIEW_PENDING whose `AutoReviewWatch` is still WATCHING is `OPTIONAL`: no header
count, no own-move filter, no alarm colour, and the `NEEDS` line drops its red with the tier. A `PROBLEM` line
does not: a broken request link is broken on a card whose move can wait.

Both halves are narrow on purpose. **Only REVIEW_PENDING**, because that is the one status where the question
is about a round comments are still arriving on; asked from anywhere else it sits in the agent's own window,
which nothing on a request can reach. **Only while the poll runs**, because a stopped one leaves the round to
this human and nobody else. The poll itself is untouched — `AutoReviewCadence.polls` asks only whether a
request is open, and a round nobody answered is exactly the one worth another sweep.

Reachable from **every** status, because an agent may ask without moving its task — and the statuses a
question is not expected from are exactly the ones nothing else flips. A closed task's leftover message is the
one exception.

### Whether the request is approved is shown, not inferred from the status

`TaskState.approved` is stamped by the same read that stamps the pipeline (`ReviewSweepService.record`, one
write for both, null until a read has said). Both surfaces show it beside the request the moment it opens —
the board as a dot next to the MR link (filled when approved, an empty ring while it waits), the console as a
prefix on the request line.

A status cannot answer this: the wait starts when the request opens, and only one status is ever the approval
itself. A new round drops it with the pipeline, since neither describes the request state that follows.

### A status says itself in words, once

`TaskStatus.label()` is the spelling both surfaces render (`out for review`, `not shipped`, `not approved`),
while the enum name stays the wire value, what `state.json` carries and what the board hangs in the chip's
tooltip.

It names a **state** and never a next move — the highlighted action already gives that, and a status that
advised too would be the third copy of one sentence.

The board binds the age **inside** that chip, because `CI_POLLING · 18m · sng` reads as three unrelated items
with the middle one anybody's guess. The console prints the same pair in the same order.

### Position does not carry state

This is what makes the board readable. A phase that owns a column has to **move** the card it describes, and
re-finding it is the cost a human pays for the arrangement; an order that follows activity moves cards on an
agent's keep-alive, which is motion nobody asked for.

So `TaskViews` orders by **alias** (numerically — plain text puts p10 before p2) for both surfaces, the board
lays one card per grid cell, and a status change repaints the chip in place. A phase is a **count** in one line
above the grid, zeros included, so that line cannot move either.

The board offers no sort control. What it offers is **narrowing** — a filter over alias, id and title, plus
needs-my-action — because a filter is an explicit act with a visible control, while automatic reordering is
what costs a human the position they had learnt.

An alias is the lowest free number, so closing one task and starting another can shift the grid by one place.
That is motion from the human's **own** action, which is the only motion this design allows.

### Three clocks, three questions, and none of them answers another's

| clock | answers | shown |
|-------|---------|-------|
| `statusSince` | time in **this** status — restarts on every real transition, including a respawned agent re-reporting itself out of REVERTED | inside the status chip |
| `lastActiveTimestamp` | liveness for the watchdog — any MCP call, keep-alives included | a console column and a tooltip line, **never** a card row |
| `TaskState.requestOpenedAt` | the review's own age | the `MR <age>` chip, which is also the link |

`statusSince` cannot answer "how long has this been waiting". `lastActiveTimestamp` on a status the agent does
not own says only that nothing has happened, which the status already said.

`requestOpenedAt` is stamped **twice, from two different clocks**: first by jagt's own the moment a request new
to the task is linked (`TaskState.relinked`) — a **floor**, so the card has an age from the first second, and
`ship`, an agent reporting the url and `resume` all reach that method. Then by the request's **own** creation
time, which replaces it on every review read (`ReviewFacts.openedAt`) and is the value that survives — asked of
both readers, the host's `created_at` and the model read's `openedAt`, because `resume` adopts requests that are
weeks old. A read that cannot say passes 0 and `withRequestOpenedAt` keeps what is there, so **a correction can
never blank an age.**

Not to be confused with `mrCreatedAt`, the round window `AutoReviewCadence` measures. That window is **per
round**, and a round begins on every **entry** into CI_POLLING — whether `ship` put it there or the agent
reported it — while a repeated CI_POLLING keeps it. Measured from the first request ever linked instead, a
task sent back out for review would be past its window on arrival.

On the board the request age is also the **link** to the request, and the task number the link to the ticket —
one element per fact, so no row spends a line naming what it points at. Several repositories mean several
requests against one stamp, so those links are named by project and ageless.

### The embedded terminal is a rendering of `focus`, never a second verb

With `orchestrator.web-terminal.enabled`, a Focus click on the board also opens the task's tmux session in a
`<dialog>`. `adapter/TtydWebTerminal` serves **one ttyd per tmux session** (not per task — a task is a window
inside one), and `POST /api/tasks/terminal?task=<id>` hands back its address, `null` meaning none is configured.

It selects no window and executes nothing; the action itself still goes through `CommandService`, so the
console keeps raising the native viewer and the card grows no button outside `Move.actions()`.

Four things it owes, none optional:

- **The terminal is writable**, because a view you cannot answer the agent in is pointless — and a writable
  terminal is a **shell**, so `--check-origin` is what makes the served page the only origin that may open a
  socket. A websocket handshake is exempt from same-origin rules, so without it any page the human visits can
  drive the agents' session over loopback. **The bind address is not that defence and never was.**
- `--exit-no-conn` ends the server with the last viewer, so a `done` that kills a tmux session cannot leave a
  ttyd behind and no port leaks.
- The port is the first **free** one from `web-terminal.port`, so a server orphaned by a `kill -9` moves the
  next one along instead of killing the feature.
- The frame is **unloaded** on close, since tmux sizes every window to its smallest attached client, including
  one nobody is looking at.

ttyd stays **one class**, not a sixth seam — a second web terminal is an interface extraction, and nothing
outside it names ttyd.
