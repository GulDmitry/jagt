# unattended-work Specification

## Purpose

What jagt does unasked: the auto-review poll, its cadence, and every other job it ticks, read back from its own log.

## Requirements

### Requirement: The auto-review poll only reads and drafts
The poll (`AutoReviewScheduler` → `ReviewSweepService`) SHALL only read and draft: threads relayed, the agent fixing locally into
`review_replies.md`, nothing posted without a human `ship`, the diff and drafts left for `ide <alias>`. Code review is never fully automated.
Rounds: [review](../review/spec.md).

#### Scenario: Sweep by hand
- **WHEN** you type `review <task>`
- **THEN** it runs the sweep
- **AND** a round that answered every comment and changed no code is not your move: the open threads are the reviewer's

### Requirement: Detection is deterministic and the one host write is a re-run
Detection SHALL be deterministic (a cadence, a status, an open request), a new trigger's too.
The one write to a code host is the re-run of a pipeline the host says died on infrastructure: [infra-wait](../infra-wait/spec.md).

#### Scenario: A new trigger
- **WHEN** a trigger is added to the poll
- **THEN** it detects by a cadence, a status or an open request, never by a model's word

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
`logback-spring.xml` SHALL drop Boot's rolling appender; `surface/ui/LogFileRoll` SHALL roll the file into `.1` before it opens
and keep the `.1` it replaces as a dated archive for 14 days.

#### Scenario: Restart
- **WHEN** jagt starts
- **THEN** the log holds this run, its `.1` the run before, and the runs before that sit beside it dated

### Requirement: `act` stops itself after three bad endings
Three tasks in a row the Master moved in `act` ending silent, in a deploy conflict or reverted SHALL stop `act`
(`MasterBrake`). A silence counts after its own last move; a deploy resets the count, a restart does not.

#### Scenario: Three tasks it moved end badly in a row
- **WHEN** the third one is stamped silent, conflicts on deploy or is reverted
- **THEN** it only judges, across restarts, until you press the header's `act stopped`
- **AND** a strike counts when the Master ships ABC-42 and its session falls silent, none where you converse in its window and it idles

### Requirement: Every Master step is seen in one window
While `roles.master.mode` is not `off`, jagt SHALL keep one `master` window in its tmux session following `jagt-master.log`, one line per Master step
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

### Requirement: Jobs that keep the machine tidy
Each of these jobs SHALL do its one thing unasked:
- `jarwatch`: each minute, tells you the jar this process runs from was rebuilt underneath it.
- `orphanscan`: at startup, deletes jagt's own worktree residue and warns about leftovers holding work or secrets.
`run <job>` SHALL make one job due at once instead of at its interval.

#### Scenario: A husk of a worktree, work that landed
- **WHEN** a worktree directory jagt cut has lost its checkout and holds only the editor's files
- **THEN** `orphanscan` deletes it at startup
- **WHEN** you run `run orphanscan`
- **THEN** it runs at once; a job already running, or one nobody declared, is refused

### Requirement: Jobs that read the tracker
Each of these jobs SHALL do its one thing unasked:
- `ticket-prefetch`: reads each working task's ticket once, for the Master's next review of it.
- `intake`: with `tracker.mode` `take` or `both`, starts a task on every item ready for one. One it cannot
  is tried again every poll, and told once.
- `ticket-close`: with `tracker.mode` `close` or `both`, closes a task whose item reached `doneStatus`.

#### Scenario: An item intake cannot start
- **WHEN** nothing routes ABC-42, and a later label names its project
- **THEN** you are told once that intake cannot start ABC-42, a restart included, and the next poll after the label starts it

#### Scenario: A finished item
- **WHEN** an item reaches `doneStatus` under `tracker.mode: close`
- **THEN** `ticket-close` closes its task

#### Scenario: A name that is no item key
- **WHEN** a name is read under `tracker.workflow` `jira` or `none`
- **THEN** `jira` takes `ABC-42`, `none` takes no name, and no read of an item is made for the rest

#### Scenario: No tracker
- **WHEN** `tracker.workflow` is `none` and a request is resumed on branch `ABC-42`
- **THEN** no tracker is asked about it

### Requirement: A task has a ceiling of unattended moves an hour
jagt SHALL count, per task, its unasked moves in the last hour since its last `lift`: history steps by the Master, auto-review, the tracker or a
retry. A board press or a session's own report SHALL NOT count. At `loopBreaker.perHour` (default 20) the next Master press, conflict deploy,
auto-review poll or round SHALL NOT be made. The task SHALL stop, its status kept, as `PACE`. A human press SHALL never be refused.

#### Scenario: Too many moves in an hour
- **WHEN** ABC-42 made 20 unattended moves in the last hour
- **THEN** the next is not made and the card asks `lift the stop`
- **AND** with some of the 20 over an hour old, or before the last lift, the next is made

### Requirement: A Markdown file may be the tracker
With `tracker.workflow: file`, jagt SHALL read the items of the Markdown file `tracker.file` itself, without a model.
A top-level `- [ ] **ABC-42 — Title**` line is an item, `[ ]` reading as `open` and `[x]` as `done`; its body runs to the
next top-level list line or heading. A key the file holds is answered from it, any other reference is read as before,
and startup refuses `file` without `tracker.file`.

#### Scenario: An unchecked item
- **WHEN** the file holds `- [ ] **ABC-42 — A title**` and `startStatus` is `open`
- **THEN** intake takes ABC-42

#### Scenario: A checked item
- **WHEN** ABC-42 is checked `[x]` and `doneStatus` is `done`
- **THEN** its task closes

#### Scenario: An unreadable file
- **WHEN** the file is missing or unreadable
- **THEN** the poll is skipped with a warning naming the cause, and no item is taken as gone

#### Scenario: A reference the file does not hold
- **WHEN** you run `do <url>` or name a key the file lacks
- **THEN** it is read through your MCP servers as without the file tracker
