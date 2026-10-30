# unattended-work Specification

## Purpose

What jagt does unasked: the auto-review poll, its cadence, and every other job it ticks, read back from its own log.

## Requirements

### Requirement: The auto-review poll only reads and drafts
The poll (`AutoReviewScheduler` → `ReviewSweepService`) SHALL only read and draft: threads relayed, the agent fixing locally into
`review_replies.md`, nothing posted without a human `ship`, the diff and drafts left for `ide <alias>`. Code review is never fully automated.
Rounds: [review](../review/spec.md). Detection SHALL be deterministic (a cadence, a status, an open request), a new trigger's too.
The one write to a code host is the re-run of a pipeline the host says died on infrastructure: [infra-wait](../infra-wait/spec.md).

#### Scenario: Sweep by hand
- **WHEN** you type `review <task>`
- **THEN** it runs the sweep
- **AND** a round that answered every comment and changed no code is not your move: the open threads are the reviewer's

### Requirement: An open request is what the poller watches
`AutoReviewCadence.polls` SHALL watch an open request, never a status: an unmerged one exists and the task is not DONE. At `REVIEWED`, `APPROVED` or
`DEPLOYED` only a reviewer moves it on: it is read every `maxIntervalMinutes`, and past its window up to `waitingMaxIntervalMinutes` (240).

#### Scenario: Comments after hand-back
- **WHEN** comments arrive after the agent handed back
- **THEN** the next poll picks them up
- **AND** threads the reviewer never resolves keep polls going; the agent is re-briefed only when the round changed

#### Scenario: Deployed, green, unapproved
- **WHEN** a deployed task comes back green and unapproved
- **THEN** it stays `deployed`, read every `maxIntervalMinutes` from the start: a poll never moves work that went out

### Requirement: The polling window is per round
The window SHALL run per round from `mrCreatedAt`, restamped on each entry into CI_POLLING, kept by a repeat, else `requestOpenedAt`.

#### Scenario: A wait on the reviewers outlives the window
- **WHEN** ABC-42 is `REVIEWED` and 36h have passed since its round began
- **THEN** it is still watched and read every 150 minutes, owned by CI with no attention asked, until approved, merged or closed

#### Scenario: Polling stopped
- **WHEN** a round not waiting on reviewers outlives `autoReview.windowHours`
- **THEN** the card says `polling stopped` and asks for you, and one banner says so, none again after a restart

### Requirement: The cadence is the whole policy
`AutoReviewCadence` SHALL be the whole policy: enabled, the interval ramp, `watch(task, now)` → `task/AutoReviewWatch` (the next-poll stamp, window
elapsed, off, or nothing). `AutoReviewScheduler.decide` SHALL translate the same watch.

#### Scenario: An open request nothing polls
- **WHEN** a request is open and nothing polls it
- **THEN** the card says `polling off` or `cannot time this`: `sweep` by hand
- **AND** a round a poll cannot read is retried, then one desktop ping names what stopped it

### Requirement: Polling is the install's property
Whether polling runs SHALL be said once above the grid (`Board.autoReview` → `cadence.summary()`), never per card. A card's countdown SHALL be an
absolute stamp (`core/format.js`).

#### Scenario: Is anything polling
- **WHEN** you ask whether anything is polling
- **THEN** the header says `auto-review on` or `off`

### Requirement: Unattended work is a declared kind
Each unattended job SHALL be a `job/Job`: id, one line for the human, an interval or `null` for once at startup, `run()`. `job/Jobs` schedules each on
the shared `TaskScheduler` once ready: never overlapping itself, the next run stamped from the start of the last, a throw booked against that job.

#### Scenario: Anything else running
- **WHEN** you ask whether anything else runs behind your back
- **THEN** the header shows the next unattended job, and one that failed; `jobs` has the detail

#### Scenario: A job names itself nothing, or outlasts its interval
- **WHEN** a job declares a blank id
- **THEN** jagt refuses to start, naming the job's class
- **AND** a run lasting longer than the job's interval is followed the moment it ends, never beside it

### Requirement: The activity report reads jagt's own log
`command/ActivityReport` SHALL tail `logging.file.name` (ECS JSON) for entries carrying a `task` key, newest first.

#### Scenario: What ran for a task
- **WHEN** you open the activity report
- **THEN** it lists jagt's unattended work on tasks, newest first

### Requirement: A restart keeps the run before
`logback-spring.xml` SHALL drop Boot's rolling appender; `surface/ui/LogFileRoll` SHALL roll the file into `.1` before it opens.

#### Scenario: Restart
- **WHEN** jagt starts
- **THEN** the log holds this run, its `.1` the run before

### Requirement: `act` stops itself after three bad endings
Three tasks in a row the Master moved in `act` ending silent, in a deploy conflict or reverted SHALL stop `act`
(`MasterBrake`). A silence counts after its own last move; a deploy resets the count, a restart does not.

#### Scenario: Three tasks it moved end badly in a row
- **WHEN** the third one is stamped silent, conflicts on deploy or is reverted
- **THEN** it only judges, across restarts, until you press the header's `act stopped`
- **AND** a strike counts when the Master ships ABC-42 and its session falls silent, none where you converse in its window and it idles

### Requirement: Every Master step is seen in one window
While `master.mode` is not `off`, jagt SHALL keep one `master` window in its tmux session following `jagt-master.log`, one line per Master step
(`MasterFeedWindow`). Closed, it SHALL come back within seconds; each start rolls the log into `.1`.

#### Scenario: You want to know why a window opened or closed
- **WHEN** the Master answers, asks, returns, starts, ships, deploys or stops `act`
- **THEN** the `master` window shows it with the task, and an answer with its question and decision

### Requirement: A second wording of a rule merges into it
When a written rule leaves `memory/routing.md` at its ceiling, one read of the whole file SHALL name two rules placing the same items
(`RoutingAssistant.sameRules`). jagt SHALL merge them only where both place in one project, adding their counts and dating the dropped wording
(`RoutingMemory.merge`).

#### Scenario: Two wordings place in one project
- **WHEN** the read names two rules placing in one project
- **THEN** the dropped one's count joins the kept one's, and it stays as a dated line
- **AND** two rules placing in different projects both stand

### Requirement: Jobs that keep the machine tidy and read the tracker
Each of these jobs SHALL do its one thing unasked: - `jarwatch`: each minute, tells you the jar this process runs from was rebuilt underneath it. -
`orphanscan`: at startup, deletes jagt's own worktree residue and warns about leftovers holding work or secrets. - `ticket-prefetch`: reads each
working task's ticket once, for the Master's next review of it. - `intake`: with `tracker.mode` `take` or `both`, starts a task on every item ready
for one, and tells you once of one it cannot. - `ticket-close`: with `tracker.mode` `close` or `both`, closes a task whose item reached `doneStatus`.
`run <job>` SHALL make one job due at once instead of at its interval.

#### Scenario: A husk of a worktree, work that landed
- **WHEN** a worktree directory jagt cut has lost its checkout and holds only the editor's files
- **THEN** `orphanscan` deletes it at startup
- **WHEN** an item reaches `doneStatus` under `tracker.mode: close`
- **THEN** `ticket-close` closes its task
- **WHEN** you run `run orphanscan`
- **THEN** it runs at once; a job already running, or one nobody declared, is refused

### Requirement: A task has a ceiling of unattended moves an hour
jagt SHALL count, per task, its unasked moves in the last hour since its last `lift`: history steps by the Master, auto-review, the tracker or a
retry. A board press or a session's own report SHALL NOT count. At `loopBreaker.perHour` (default 20) the next Master press, conflict deploy,
auto-review poll or round SHALL NOT be made. The task SHALL stop, its status kept, as `PACE`. A human press SHALL never be refused.

#### Scenario: Too many moves in an hour
- **WHEN** ABC-42 made 20 unattended moves in the last hour
- **THEN** the next is not made and the card asks `lift the stop`
- **AND** with some of the 20 over an hour old, or before the last lift, the next is made

### Requirement: jagt stops a task it suspects it is looping
`TaskStops` SHALL stop a task, its status kept, on any trigger since the last `lift`: `loopBreaker.stalledRounds` (3) relayed rounds running that
each brought no new note and left the same job failing; a Master ship past `loopBreaker.ships` (4) within `autoReview.windowHours` that answered a
round bringing no new note; a failed verification or probe, a not-ready plan or round, or a review round, sent back past
`loopBreaker.rounds` (3) running in 24h. A round counts only while it answers the session report the one before answered.
Every stop SHALL append a line `{at, task, alias, trigger, status, evidence}` to `stops.jsonl` beside
`state.json` (rolled at 1 MB), which `lift` leaves.

#### Scenario: Reworked plans are work
- **WHEN** ABC-42's plan is returned a fourth time and each of the three before answered a different plan
- **THEN** it is relayed and nothing stops

#### Scenario: Rounds that resolve nothing
- **WHEN** ABC-42 gets a fourth relayed round with the same newest notes and `unit-tests` failing, as the three before
- **THEN** nothing is relayed, the card asks `lift the stop`, and one notification names the rounds

#### Scenario: A new note resets the count
- **WHEN** ABC-42 shipped five times today, every round before a ship bringing a new note, and the Master would ship again
- **THEN** it ships, and nothing stops it
- **AND** a new thread or note, by anyone, restarts the `stalledRounds` count: two idle rounds, a note, two idle rounds count two

#### Scenario: Ships going round
- **WHEN** ABC-42 shipped four times today and no round before a ship brought a new note, and the Master would ship again
- **THEN** it presses nothing and the hover says `ships without a new note in 24h: 4`
- **AND** a ship counts unless the round it answered carries a fingerprint other than the round before it; the first round counts
- **AND** nothing is pressed on the tree the last ship was pressed on, untracked paths included; a changed or unreadable one is due
- **AND** after three such ships, once !571 merged, a fourth is refused with no step recorded, so it never counts and nothing stops

#### Scenario: Red rounds running
- **WHEN** ABC-42's verification fails a fourth time with no other step sent between, or the Master returns it a fourth
- **THEN** nothing is sent, nobody is asked, it stops where it is, and the hover says `verify sent 3 rounds running in 24h`

#### Scenario: A result outlives its round
- **WHEN** ABC-42's probes finish failed after you shipped it
- **THEN** nothing is relayed, reported, recorded or stopped, and the log says `verification outdated`; one on its own round is relayed as before

### Requirement: A stopped task waits for your lift
Nothing unattended SHALL poll, relay into, read, answer, ship or deploy a stopped task (`StopGuard`). A job's `Watch` admits one only where it
names the stop kind it ends. The board SHALL ring it `jagt stopped`, its move line `lift the stop` followed by the stop's short reason. That
line's hover SHALL carry the evidence, the trigger's advice and the stop's time in UTC. A human's `lift` or `deploy` SHALL clear it, and a new
request replacing the one the stop was counted on SHALL too. The count starts anew. A stop an outside condition set (`CHECKS`, `INFRA`) SHALL also
end on a read listing the task's checks not red ([infra-wait](../infra-wait/spec.md)). Only SHIPS, STALLED, CHECKS, UNCHANGED_FAILURE and INFRA
stops are counted on a request; the others SHALL stand.

#### Scenario: A stopped task says why
- **WHEN** ABC-42 is stopped on SHIPS
- **THEN** its card reads `lift the stop — ships with no new note`
- **AND** the hover shows the evidence, its trigger's advice, and when it stopped

#### Scenario: A nudge is left unsent while a task is stopped
- **WHEN** ABC-42 stands stopped with jagt's nudge unsent in its window
- **THEN** nothing presses Enter until the lift; the merged-request and ticket reads still run

#### Scenario: You press a verb on a stopped task
- **WHEN** you press `ship` or `deploy` on a stopped task
- **THEN** it runs, and the stop stands until you press `lift`, but a `deploy` lifts it once `DEPLOYED`
- **AND** a deploy any other origin presses is refused and lifts nothing

#### Scenario: A new request replaces the one the stop was counted on
- **WHEN** ABC-42 was stopped on !571 and opens !573
- **THEN** the stop lifts, the count restarts, the sweep reads !573, and the log says `stop lifted` with `cause=new request`
