# unattended-work Specification

## Purpose

What jagt does unasked: the auto-review poll, its cadence, and every other job it ticks, read back from its own log.

## Requirements

### Requirement: The auto-review poll only reads and drafts
The poll (`AutoReviewScheduler` → `ReviewSweepService`) SHALL only read and draft: threads relayed, the agent fixing locally into
`review_replies.md`, nothing posted without a human `ship`, the diff and drafts left for `ide <alias>`. Code review is never
fully automated. Rounds: [review](../review/spec.md).

#### Scenario: Sweep by hand
- **WHEN** you type `review <task>`
- **THEN** it runs the sweep

#### Scenario: Not your move
- **WHEN** a round answered every comment and changed no code
- **THEN** it is not your move: the open threads are the reviewer's

### Requirement: Detection is deterministic
Detection SHALL be deterministic — a cadence, a status, an open request; a new trigger MUST be too.

#### Scenario: A new trigger
- **WHEN** a new auto-review trigger is added
- **THEN** it is deterministic too

### Requirement: An open request is what the poller watches
`AutoReviewCadence.polls` SHALL watch an open request, never a status: a request exists and the task is not DONE.

#### Scenario: Comments after hand-back
- **WHEN** comments arrive after the agent handed back
- **THEN** the next poll picks them up

#### Scenario: Threads never resolved
- **WHEN** the reviewer never resolves the threads
- **THEN** polls continue; the agent is re-briefed only when the round changed

#### Scenario: Deployed, green, unapproved
- **WHEN** a deployed task comes back green and unapproved
- **THEN** it stays `deployed`: a poll never moves work that went out

### Requirement: The polling window is per round
The window SHALL run per round from `mrCreatedAt`, restamped on each entry into CI_POLLING, kept by a repeat, else `requestOpenedAt`.

#### Scenario: Polling stopped
- **WHEN** the round outlives `autoReview.windowHours`
- **THEN** the card says `polling stopped` and asks for you

### Requirement: The cadence is the whole policy
`AutoReviewCadence` SHALL be the whole policy: enabled, the interval ramp, `watch(task, now)` → `task/AutoReviewWatch` (the
next-poll stamp, window elapsed, off, or nothing). `AutoReviewScheduler.decide` SHALL translate the same watch.

#### Scenario: An open request nothing polls
- **WHEN** a request is open and nothing polls it
- **THEN** the card says `polling off` or `cannot time this`: `sweep` by hand

#### Scenario: Unreadable round
- **WHEN** a poll cannot read the round
- **THEN** it is retried, then one desktop ping names what stopped it

### Requirement: Polling is the install's property
Whether polling runs SHALL be said once above the grid (`Board.autoReview` → `cadence.summary()`), never per card. A card's
countdown SHALL be an absolute stamp (`core/format.js`).

#### Scenario: Is anything polling
- **WHEN** you ask whether anything is polling
- **THEN** the header says `auto-review on` or `off`

### Requirement: Unattended work is a declared kind
Each unattended job SHALL be a `job/Job`: id, one line for the human, an interval or `null` for once at startup, `run()`.
`job/Jobs` ticks it, the one ticker: one thread per run, never overlapping, a throw booked against that job.

#### Scenario: Anything else running
- **WHEN** you ask whether anything else runs behind your back
- **THEN** the header shows the next unattended job, and one that failed; `jobs` has the detail

#### Scenario: A job names itself nothing
- **WHEN** a job declares a blank id
- **THEN** jagt refuses to start, naming the job's class

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
(`MasterBrake`); a deploy resets the count.

#### Scenario: Three tasks it moved end badly in a row
- **WHEN** the third one is stamped silent, conflicts on deploy or is reverted
- **THEN** it only judges, across restarts, until you press the header's `act stopped`

### Requirement: Every Master step is seen in one window
While `master.mode` is not `off`, jagt SHALL keep one `master` window in its tmux session following
`jagt-master.log`, one line per Master step (`MasterFeedWindow`). Closed, it SHALL come back within seconds; each start rolls the log into `.1`.

#### Scenario: You want to know why a window opened or closed
- **WHEN** the Master answers, asks, returns, starts, ships, deploys or stops `act`
- **THEN** the `master` window shows it with the task, and an answer with its question and decision

### Requirement: A second wording of a rule merges into it
When a written rule leaves `memory/routing.md` at its ceiling, one read of the whole file SHALL name two rules
placing the same items (`RoutingAssistant.sameRules`). jagt SHALL merge them only where both place in one project,
adding their counts and dating the dropped wording (`RoutingMemory.merge`).

#### Scenario: Two wordings place in one project
- **WHEN** the read names two rules placing in one project
- **THEN** the dropped one's count joins the kept one's, and it stays as a dated line

#### Scenario: The read names rules placing in different projects
- **WHEN** the two rules place in different projects
- **THEN** both stand

### Requirement: Jobs that keep the machine tidy
Each of these jobs SHALL do its one thing unasked:
- `jarwatch`: each minute, tells you the jar this process runs from was rebuilt underneath it.
- `orphanscan`: at startup, deletes jagt's own worktree residue and warns about leftovers holding work or secrets.

#### Scenario: A husk of a worktree
- **WHEN** a worktree directory jagt cut has lost its checkout and holds only the editor's files
- **THEN** `orphanscan` deletes it at startup

### Requirement: Jobs that read the tracker
Each of these jobs SHALL do its one thing unasked:
- `ticket-prefetch`: reads each working task's ticket once, for the Master's next review of it.
- `intake`: with `tracker.mode` `take` or `both`, starts a task on every item ready for one.
- `ticket-close`: with `tracker.mode` `close` or `both`, closes a task whose item reached `doneStatus`.

#### Scenario: The work landed
- **WHEN** an item reaches `doneStatus` under `tracker.mode: close`
- **THEN** `ticket-close` closes its task

### Requirement: A job runs now when asked
`run <job>` SHALL make one job due at the next tick instead of at its interval.

#### Scenario: Running a job now
- **WHEN** you run `run orphanscan`
- **THEN** it runs at the next tick; a job already running, or one nobody declared, is refused

### Requirement: Unattended starts are counted per day
jagt SHALL count the starts it makes unasked per local day, in memory, from zero on a restart: a Master move in `act`
that starts work (`MasterPress`, `ConflictHandOff`), a tracker intake start (`IntakeJob`) or an auto-review sweep
(`AutoReviewScheduler`). Closing a ticket, a Master judge read and a ticket prefetch SHALL NOT count.

#### Scenario: A ticket closed unasked
- **WHEN** the tracker close ends a task
- **THEN** the day's count stays as it was

### Requirement: Unattended starts have a daily ceiling
At `orchestrator.maxStartsPerDay` (default 50) jagt SHALL write the dated file `starts-stopped` beside `state.json`,
separate from `act-stopped`. While it stands the Master SHALL only judge, and intake and auto-review SHALL start
nothing; a human press SHALL never be refused. It SHALL lift on the next local day, or when you press the header's
`act stopped`, which names its cause in the hover.

#### Scenario: The ceiling is reached
- **WHEN** the 50th unattended start of a local day is taken
- **THEN** the Master only judges, intake and auto-review start nothing, and the header shows `act stopped`
  whose hover names the 50

#### Scenario: The stop lifts
- **WHEN** the next local day begins, or you press `act stopped` while the daily stop stands
- **THEN** it is gone and starts count from zero

### Requirement: jagt stops a task it suspects it is looping
`LoopGuard` SHALL stop a task after `loopBreaker.stalledRounds` (3) relayed rounds running that each left as many
threads open and the same job failing. It SHALL stop one before the Master ships past `loopBreaker.ships` (4) within
`autoReview.windowHours` since the last `lift`. A ship counts only if the round it answers did not bring the open
threads below the lowest of the rounds before it; the first round sets that low. Its status SHALL stay.

#### Scenario: Rounds that resolve nothing
- **WHEN** ABC-42 gets a fourth relayed round with 10 threads open and `unit-tests` failing, as the three before
- **THEN** nothing is relayed, the card asks `lift the stop`, and one notification names the rounds

#### Scenario: Ships with progress
- **WHEN** ABC-42 shipped five times today while its threads fell from 10 to 5, and the Master would ship again
- **THEN** it ships, and nothing stops it

#### Scenario: Ships going round
- **WHEN** ABC-42 shipped four times today while its threads went 5, 6, 5, 6, and the Master would ship again
- **THEN** it presses nothing and the hover says `ships without a new low in 24h: 4`

### Requirement: A stopped task waits for your lift
Nothing unattended SHALL poll, relay into, read, answer, ship or deploy a stopped task (`LoopStopGuard`). The board
SHALL ring it `jagt stopped`, its hover the evidence. Only a human's `lift` SHALL clear it and count anew.

#### Scenario: You press a verb on a stopped task
- **WHEN** you press `ship` or `deploy` on a stopped task
- **THEN** it runs, and the stop stands until you press `lift`
