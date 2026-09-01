# Whose move it is

[← AGENTS.md](../../AGENTS.md)

## A blocked session is on the board, whatever blocked it

- `outcome=question` goes in *before* the human sees the question — rule 1 of `sub-agent-context.md` and of
  the `update_agent_status` description. `AgentReport.QUESTION` → `Move.owner` YOU from any status, `DashboardLine`
  NEEDS INPUT, one `AgentStatusReports` ping on the transition **into** asking.
- Silence — token limit, crash, unanswered prompt, or `SessionProbe.State.IDLE` at the shared threshold with
  nothing newer behind it — is stamped by `WatchdogService` from `service/SessionProbe`
  (`TaskState.silentSince`, `TaskState.silentBecause`): same flip, a NEEDS YOU line, overruled at NEW.
- The blocking wording is **declared**, not matched: `blocking-notification` in the runtime's properties →
  `AgentRuntime.blockingNotification`; unrecognised wording stays the quieter.
- A hook reports, it decides nothing: the stamp is the watchdog's one verdict, on the **transition** only,
  cleared by **any** report (`withStatus`), a later *alive* winning by arrival.
- Watched: every status whose `Move.ownerOf` is AGENT (`WatchdogServiceTest`).

## `Owner` means an action of theirs exists

`Phase` and `Owner` are an unpersisted projection, never a second state machine: `TaskStatus` is the SSOT,
twelve statuses collapsing into six phases. Liveness is no input — SHIPPING is offered SHIP, the
gate refusing at execution time if the agent is alive.

- Beyond the status (`Move.ownerOf`): a REVIEW_PENDING round that changed nothing and drafted no reply waits
  on the **reviewer**, one whose poll `AutoReviewWatch.stopped()` on the human.
- REVIEWED's owner is the review request: nothing highlighted, no ping, `deploy` still listed where no
  approval is needed. APPROVED is the one the human is tapped for — `AgentStatusReports.ping` is silent
  unless `Move` says YOU. DEPLOYED's owner is nobody, as DONE's; `done` stays highlighted.

## An action that can wait is not an interruption

`flow/Attention` names the tier, pinned over every status in `MoveTest`.

- `REQUIRED` — a stopped or asking session, a round back, a red run, a conflict, a round nothing reads again:
  header count, own-move filter, alarm edge.
- `OPTIONAL` — an approval landed, their own revert: a card badge, nothing louder.
- `NONE` — the owner is not YOU: nothing.
- The badge names the act, never the tier (`Move.ask`): the highlighted verb, or the card where the act is
  reading; `Move.hint` says it at sentence length, as its tooltip.
- The quiet tier says so in grammar, not colour — an imperative against an offer.

## A question outranks the status it was asked from

- A question back **with** its round: REVIEW_PENDING badges `review the round` and hints `focus`, anywhere
  else `answer the session` is the whole move; `Move.primaryOf` stays FOCUS.
- A question at REVIEW_PENDING still WATCHING is `OPTIONAL` — no count, no filter, no alarm colour, `NEEDS`
  losing its red; a `PROBLEM` line does not.
- A closed task's leftover message cannot flip one.
- Approval is shown, not inferred (`TaskState.approved`, stamped with the pipeline by
  `ReviewSweepService.record`, null until read): an empty ring beside the request until it lands, dropped on a
  new round.

## A status says itself in words; position carries nothing

- `TaskStatus.label()` is what the board renders (`out for review`, `not shipped`, `not approved`); the enum
  name is the wire value and what `state.json` carries. It names a state, never a move.
- `TaskViews` orders by **alias**, numerically, a status change repainting the chip in place. A phase is a **count** above the grid, zeros included.
- No sort control, only **narrowing**: a filter over alias, id and title, plus needs-my-action. An alias is
  the lowest free number.

## Three clocks, three questions

- `statusSince` — time in **this** status, restarting on every real transition.
- `lastActiveTimestamp` — watchdog liveness, any MCP call including keep-alives; shown nowhere. Never probe
  window activity instead: a window waiting on a question repaints every 10-30s, so nothing is ever stamped.
- `TaskState.requestOpenedAt` — the review's age, in the `MR <age>` chip: a **floor** at `TaskState.relinked`,
  replaced on every review read by the request's own creation time (`ReviewFacts.openedAt`, both readers); a
  read that cannot say passes 0, `withRequestOpenedAt` keeping what is there.
