# attention Specification

## Purpose

Whose move it is: how a blocked session reaches the board, what interrupts, and what the clocks mean.

## Requirements

### Requirement: A question is reported first
An agent MUST report `outcome=question` before the human sees the question, rule 1 of `sub-agent-context.md` and
`update_agent_status`. `AgentReport.QUESTION` SHALL make `Move.owner` YOU from any status, the
`DashboardLine` NEEDS INPUT, and one `AgentStatusReports` ping on the transition into asking.

#### Scenario: An agent stops to ask
- **WHEN** an agent asks
- **THEN** NEEDS INPUT, one desktop ping the first time

#### Scenario: The task contradicts the code
- **WHEN** it breaks a code guarantee
- **THEN** the agent asks before the code picks a side; "the ticket wins" is yours

#### Scenario: Settled unasked
- **WHEN** you `focus <task>`
- **THEN** an `OPEN QUESTIONS:` line ends its output

### Requirement: Silence is stamped by the watchdog
`WatchdogService` SHALL stamp silence from `service/SessionProbe` (`TaskState.silentSince`, `TaskState.silentBecause`):
token limit, crash, unanswered prompt, or `SessionProbe.State.IDLE` at the shared threshold with nothing newer
behind it: the same flip, a NEEDS YOU line, overruled at `NEW`. It MUST watch every status whose `Move.ownerOf`
is AGENT (`WatchdogServiceTest`).

#### Scenario: Silent stop
- **WHEN** a session stops unannounced
- **THEN** NEEDS YOU, Focus highlighted

#### Scenario: A permission prompt
- **WHEN** a session sits at one
- **THEN** it is reported within seconds; a plain turn end is silent until nothing moved for a while

#### Scenario: A turn ends unreported
- **WHEN** Claude ends its turn without reporting
- **THEN** it is sent back once to report first, then stamped silent

#### Scenario: The agent CLI never came up
- **WHEN** you `focus <task>` at `NEW`
- **THEN** the card points at the launch

### Requirement: A hook reports, the watchdog decides
Blocking wording MUST be declared, never matched: `blocking-notification` in the runtime's properties →
`AgentRuntime.blockingNotification`, unrecognised wording staying the quieter. The stamp SHALL be the watchdog's
one verdict, on the transition only, cleared by any report (`withStatus`), a later alive winning by arrival.

#### Scenario: A stamped task reports
- **WHEN** any report arrives
- **THEN** the stamp clears

### Requirement: Owner is a projection
`Phase` and `Owner` SHALL be an unpersisted projection of `TaskStatus`, the SSOT, never a second state machine.
`Owner` means an action of theirs exists. Liveness MUST be no input: `SHIPPING` is offered SHIP, the gate refusing
at execution if the agent is alive.

#### Scenario: SHIP, agent alive
- **WHEN** pressed
- **THEN** the gate refuses

### Requirement: Owner beyond the status
Beyond `Move.ownerOf`, a `REVIEW_PENDING` round changing nothing and drafting no reply SHALL wait on the reviewer,
one whose poll `AutoReviewWatch.stopped()` on the human. A round the Master has not read (`RoundState.masterReading`)
SHALL be AGENT: `master review`, no badge.

#### Scenario: Master reading
- **WHEN** the round is unread
- **THEN** the chip says `master review`

### Requirement: Owners after review
`REVIEWED`'s owner SHALL be the review request: nothing highlighted, no ping, `deploy` listed where no approval is
needed. Only `APPROVED` taps the human; `AgentStatusReports.ping` MUST stay silent unless `Move` says
YOU. `DEPLOYED`'s owner is nobody, as `DONE`'s; `done` stays highlighted.

#### Scenario: An approval lands
- **WHEN** `APPROVED`
- **THEN** you are pinged

### Requirement: Attention tiers
`flow/Attention` SHALL name the tier, pinned per status in `MoveTest`. `REQUIRED` (a stopped or asking
session, a round back, a red run, a conflict, a round nothing reads again): header count, own-move filter, alarm
edge. `OPTIONAL` (an approval landed, their own revert): a card badge only. `NONE` (owner not YOU): nothing.

#### Scenario: Owner not YOU
- **WHEN** `NONE`
- **THEN** nothing shows

### Requirement: The badge names the act
The badge MUST name the act, never the tier (`Move.ask`): the highlighted verb, or the card where the act is reading;
`Move.hint` SHALL say it at sentence length as tooltip. The quiet tier speaks in grammar, not colour: an
imperative against an offer.

#### Scenario: A move that can wait
- **WHEN** `OPTIONAL`
- **THEN** the badge offers

### Requirement: A question outranks its status
A question back with its round SHALL badge `review the round`, hint `focus`, at `REVIEW_PENDING`; elsewhere
`answer the session` is the move; `Move.primaryOf` stays FOCUS. At `REVIEW_PENDING` still WATCHING it MUST be
`OPTIONAL`: no count, filter or alarm colour, `NEEDS` losing its red; a `PROBLEM` line does not. A closed task's
leftover message cannot flip one.

#### Scenario: A question while watching
- **WHEN** asked at `REVIEW_PENDING` WATCHING
- **THEN** `NEEDS` is not red

### Requirement: The board says, never infers
The board SHALL render `TaskStatus.label()` (`out for review`, `not shipped`, `not approved`), a state, never a
move; the enum name is the wire value `state.json` carries. `ReviewSweepService.record` SHALL stamp
`TaskState.approved` with the pipeline, null until read: an empty ring beside the request until it lands.

#### Scenario: A new round
- **WHEN** it opens
- **THEN** approval drops

### Requirement: Position carries nothing
`TaskViews` SHALL order by registration, a status change repainting the chip in place. A phase is a count above the
grid, zeros included. One order control, `static/ui/order.js`, MUST offer registration or alias; all else narrows:
a filter over alias, id and title, plus needs-my-action. An alias is the lowest free number, so alias order puts a
new task where a retired one sat.

#### Scenario: A status changes
- **WHEN** a task moves
- **THEN** its card stays put

### Requirement: Three clocks, three questions
`statusSince` SHALL be time in this status, restarting on every real transition. `lastActiveTimestamp` is watchdog
liveness from any MCP call, keep-alives included, shown nowhere; window activity MUST never stand in: a window
waiting on a question repaints every 10-30s, so nothing is ever stamped.

#### Scenario: A transition
- **WHEN** status changes
- **THEN** `statusSince` restarts

### Requirement: The request's age
`TaskState.requestOpenedAt` SHALL be the `MR <age>` chip: a floor at `TaskState.relinked`, replaced on each review
read by the request's creation time (`ReviewFacts.openedAt`, both readers); a read that cannot say MUST pass 0,
`withRequestOpenedAt` keeping what is there.

#### Scenario: A read passes 0
- **WHEN** it lands
- **THEN** the age stays

### Requirement: Red checks are the session's
At `verifying` jagt SHALL run the project's `verifyCommand`, a red run going to the session, not you. A deployed
change MUST be checked by its session once per deploy, a failure its new round ([task-flow](../task-flow/spec.md)).

#### Scenario: A red verification
- **WHEN** the run goes red
- **THEN** the session gets it

### Requirement: Reaching a session
`focus <task>` SHALL select the agent's window and raise the viewer, or say why not. The agent MUST live in tmux:
a closed viewer stops nothing; after a backend restart the next call reaches the new process. The
board SHALL stay local unless `--server.address=0.0.0.0`: it has no password, and it can deploy. A worktree is
briefed once, at creation; one older than a brief change keeps the old wording.

#### Scenario: The viewer was closed
- **WHEN** you press Focus
- **THEN** another viewer opens

#### Scenario: A brief changed
- **WHEN** a session uses old wording
- **THEN** answer in the window, or recreate the task
