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

**The half no prompt can promise** is the agent that never got the chance — a token limit, a crash. So
`WatchdogService` **stamps** what it probes (`TaskState.silentSince`: stale MCP plus a quiet tmux window)
instead of only sending a ping a human dismisses, and the same owner flip plus a NEEDS YOU line happen with
the agent saying nothing.

**What the watchdog cannot see is a live session sitting at a prompt**, and that is measured, not assumed: a
Claude window waiting on a question repaints every 10–30s (2026-08-20), so the window half of the probe stays
warm forever and no stamp is ever written. An agent asking anything — its own question tool, a plan to
approve, a permission prompt — is therefore reachable **only** through its own `outcome=question` report.

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

A status alone cannot always say so. The board's badge, its "action required" count and its own-move filter
all read the tier **below**, which is NONE exactly when the owner is not YOU — so a card that asks for a human
who has nothing to do teaches them to ignore all three.

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
*before* an approval. Its owner is the **code host**, nothing is highlighted on the card, and no desktop ping
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

The words come from the enum (`attentionLabel` on the wire, exactly as a status ships its label), so the
console prints the same two.

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
inside one), and `POST /api/tasks/{id}/terminal` hands back its address, `null` meaning none is configured.

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
