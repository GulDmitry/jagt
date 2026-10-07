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
The window SHALL run per round from `mrCreatedAt`, restamped on every entry into CI_POLLING and kept by a repeat, with
`requestOpenedAt` the fallback.

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

### Requirement: The activity report reads jagt's own log
`command/ActivityReport` SHALL tail `logging.file.name` (ECS JSON) for entries carrying a `task` key, newest first.

#### Scenario: What ran for a task
- **WHEN** you open the activity report
- **THEN** it lists jagt's unattended work on tasks, newest first

### Requirement: One run, one log
`logback-spring.xml` SHALL drop Boot's rolling appender, and `surface/ui/LogFileReset` SHALL empty the file before that appender
opens.

#### Scenario: Restart
- **WHEN** jagt starts
- **THEN** the log holds this run only

### Requirement: A second wording of a rule merges into it
When a written rule leaves `memory/routing.md` at its ceiling, one read of the whole file SHALL name two rules
placing the same items (`MasterAssistant.sameRules`). jagt SHALL merge them only where both place in one project,
adding their counts and dating the dropped wording (`RoutingMemory.merge`).

#### Scenario: Two wordings place in one project
- **WHEN** the read names two rules placing in one project
- **THEN** the dropped one's count joins the kept one's, and it stays as a dated line

#### Scenario: The read names rules placing in different projects
- **WHEN** the two rules place in different projects
- **THEN** both stand
