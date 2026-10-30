# attention Specification

## Purpose

Whose move it is: how a blocked session reaches the board, what interrupts, and what the clocks mean.

## Requirements

### Requirement: A question is reported first
An agent MUST report `outcome=question` before the human sees the question (rule 1 of the brief's `context` step).
`AgentReport.QUESTION` SHALL make `Move.owner` whoever `Turns.next` names, from any status, and the `DashboardLine`
NEEDS INPUT. `YourMoveJob` SHALL ping once the projection turns a task YOU, or changes its advice while YOU, a report
or not. The move must have stood five seconds; a card already yours when jagt starts is not pinged.

#### Scenario: An agent stops to ask
- **WHEN** an agent asks
- **THEN** NEEDS INPUT, one desktop ping the first time
- **AND** where the Master answers it in `act`, the card shows the Master at work and nobody is pinged until it gives up

#### Scenario: The task contradicts the code
- **WHEN** it breaks a code guarantee
- **THEN** the agent asks before the code picks a side; "the ticket wins" is yours

#### Scenario: Settled unasked
- **WHEN** you `focus <task>`
- **THEN** an `OPEN QUESTIONS:` line ends its output

#### Scenario: A step the Master may not take
- **WHEN** its verdict leaves a plan or a round for you (`mine` names it)
- **THEN** you are pinged once; a step the Master presses within five seconds pings nobody

### Requirement: Silence is read by the watchdog and recorded by the reducer
Each tick `WatchdogService` SHALL emit one `SessionSign.Read` per open task: its window's process (running, a dead
shell, missing), print, log and the threshold; a window it cannot list emits nothing. Only the reducer writes what it
finds into `TaskState.session`: a dead process at any status, an API error ending a turn, or a turn end with nothing
newer behind it past the threshold: the same flip, a NEEDS YOU line, overruled at `NEW`. It alerts only where
`Move.ownerOf` is AGENT, not `VERIFYING`.

#### Scenario: Silent stop
- **WHEN** a session stops unannounced
- **THEN** NEEDS YOU, Focus highlighted; logged and notified once per silence, a restart included, a new one alerting again
- **WHEN** a stopped session's worktree calls MCP, or a read finds a gone session's window running its agent
- **THEN** the card reads working and the banner is not repeated; the next read decides again
- **WHEN** a session ends with a reason declared as `continuing-end` (cleared, resumed)
- **THEN** it is recorded as `IDLE`, not as gone; no banner is sent

#### Scenario: The window shows the process
- **WHEN** nothing moved for the threshold and the window runs its agent
- **THEN** quiet, "no sign of life, process running": still yours to look at
- **WHEN** the window holds no agent process, at any open status, `NEW` included (`Error: Invalid MCP configuration`)
- **THEN** within seconds the session is gone, NEEDS YOU, its last lines the cause (`WatchdogServiceTest`)
- **WHEN** a ship, the session-alive stop or the restart ask cannot list the windows
- **THEN** ship refuses naming the read, the stop stands, nothing is asked

#### Scenario: A permission prompt
- **WHEN** a session sits at one and its report is plain
- **THEN** within seconds NEEDS INPUT, answer the session, no stopped banner, no key sent; a plain turn end is silent until nothing moved for a while
- **WHEN** it is the runtime's `trust-dialog` at `NEW`, jagt reads the window alone
- **THEN** the human gets one desktop notification naming its cause, not one per tick
- **WHEN** work starts again
- **THEN** a permission the prompt raised clears; one the session said stays, also after it reported

#### Scenario: A turn ends unreported, or a question answered in the terminal
- **WHEN** Claude ends its turn without reporting, or the turn that began with the human's terminal answer to its question does
- **THEN** it is sent back once to report first, so NEEDS INPUT does not outlive its answer; still unreported, it is silent

#### Scenario: A turn ends on an API error
- **WHEN** the CLI ends a turn on an API error and names `overloaded`
- **THEN** NEEDS YOU at once with no threshold, saying its turn failed: overloaded, the CLI's own error word

### Requirement: A hook reports, the watchdog decides
Blocking wording MUST be declared, never matched: `blocking-notification` in the runtime's properties →
`AgentRuntime.blockingNotification`, unrecognised wording staying the quieter. The stop SHALL be the reducer's
alone, on the transition only, cleared by any report (`withStatus`), a later alive sign winning by arrival.

#### Scenario: A stamped task reports
- **WHEN** any report arrives
- **THEN** the stop clears

### Requirement: Owner is a projection
`Phase` and `Owner` SHALL be an unpersisted projection of `TaskStatus`, the SSOT, never a second state machine.
`Owner` means an action of theirs exists. Liveness MUST be no input: `SHIPPING` is offered SHIP, the gate refusing
at execution if the agent is alive.

#### Scenario: SHIP, agent alive
- **WHEN** pressed
- **THEN** the gate refuses

### Requirement: Owner beyond the status
Beyond `Move.ownerOf`, a `REVIEW_PENDING` round changing nothing and drafting no reply SHALL wait on the reviewer,
one whose poll `AutoReviewWatch.stopped()` on the human. A plan or round the Master has not read
(`RoundState.masterReading`, `FlowRules.readByTheMaster`) SHALL be AGENT: `master review`, no badge.

#### Scenario: Master reading
- **WHEN** the plan or round is unread
- **THEN** the chip says `master review`

### Requirement: Owners after review
`REVIEWED`'s owner SHALL be the review request: nothing highlighted, no ping, `deploy` listed where no approval is
needed. Only `APPROVED` taps the human, and neither is theirs where the Master deploys it in `act`; `YourMoveJob`
MUST stay silent unless `Move` says YOU. `DEPLOYED`'s owner is nobody, as `DONE`'s; `done` stays highlighted.

#### Scenario: An approval lands
- **WHEN** `APPROVED`
- **THEN** you are pinged; once !571, its only request, is merged, the card reads `merged` and the badge `you can close the task`

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
The board SHALL render `TaskStatus.label()` (`out for review`, `not shipped`, `awaiting approval`), a state, never a
move; the enum name is the wire value `state.json` carries. `RoundRecord.record` SHALL stamp
`TaskState.approved` with the pipeline, null until read: an empty ring beside the request until it lands.

#### Scenario: A new round
- **WHEN** it opens
- **THEN** approval drops

### Requirement: Position carries the tier
`TaskViews` SHALL put the tasks waiting on the human first, then the rest in project order, each group in
registration order. A phase is a count above the grid, zeros included. One order control, `static/ui/order.js`, MUST
offer registration or alias; all else narrows: a filter over alias, id and title, typed in the filter box alone.
An alias is the lowest free number, so alias order puts a new task where a retired one sat.

#### Scenario: A status changes
- **WHEN** a task's tier changes to waiting on the human
- **THEN** its row moves into the band on top

#### Scenario: The rest
- **WHEN** no tier changes
- **THEN** the other rows keep their place, in project order

#### Scenario: The filter
- **WHEN** the human types in the filter box
- **THEN** rows narrow by alias, id or title, and no checkbox narrows them by tier

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
