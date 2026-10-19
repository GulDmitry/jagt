# usage-limits Specification

## Purpose
What the agent CLI's subscription has used, shown and told, never a gate.

## Requirements

### Requirement: Limits are read through the CLI's own login
A `usage` job SHALL read the subscription's windows and paid usage every `usageLimits.every` through
`port/SubscriptionUsage`, one adapter per `orchestrator.agent.cli`. The stored login is read at each call and never
kept or refreshed. `usageLimits.enabled: false` reads, tells and shows nothing.

#### Scenario: An expired login
- **WHEN** the stored token has expired
- **THEN** the read is unreadable, its cause naming that running the CLI refreshes it

#### Scenario: A changed answer
- **WHEN** the endpoint answers a shape the adapter does not know
- **THEN** the read is unreadable, logged with its cause, never zero

#### Scenario: An API key
- **WHEN** the CLI is signed in without a subscription
- **THEN** the answer is no subscription, apart from unreadable

#### Scenario: Rate limited
- **WHEN** the endpoint answers 429
- **THEN** no call is sent until its `Retry-After`, else 5 minutes, has passed

### Requirement: A limit is told once per period, never gated
The job SHALL send a `USAGE` banner (`job/UsageAlerts`) once per window period and SHALL never refuse or slow a task.

#### Scenario: A window fills
- **WHEN** the weekly window reaches 96% with thresholds 80 and 95
- **THEN** one banner says `week window at 95%`, and no later poll before its reset repeats it

#### Scenario: Money starts flowing
- **WHEN** a window reads 100% while paid usage is enabled, or paid usage rises
- **THEN** one banner says `paid usage started`, with what is spent of the limit

#### Scenario: Paid usage nears its limit
- **WHEN** paid usage reaches a `paidThresholds` share of its monthly limit
- **THEN** one banner says so, until it reads below that share again

### Requirement: Stats shows the limits
`stats` SHALL open with the last read (`UsageStatsRenderer`): each window's use and reset, and paid usage spent of its
limit. It adds no board mark, card element or colour.

#### Scenario: A failed read
- **WHEN** the last read failed
- **THEN** `stats` says `could not read` with its cause, never that no limit applies

#### Scenario: Nothing read yet
- **WHEN** no read has run
- **THEN** `stats` carries no limits section

### Requirement: jagt's own spend outlives a restart
Every metered call SHALL add to `usage-by-day.jsonl` beside `state.json`, one line per period and kind (`UsageLedger`):
a day for 90 days, then its month for 24 months, then its year, folded on every write so the file stays bounded.
Each line's `usage` holds `maxCallCostUsd`, the dearest single call of that line, folded with max where the sums fold
with +; a line written before it was kept reads as 0.
It adds no board mark.

#### Scenario: A day ages past ninety
- **WHEN** a call is written after a day turned 90 days old
- **THEN** that day's line folds into its month's

#### Scenario: A line jagt cannot parse
- **WHEN** a line no longer parses
- **THEN** it is written back as it was, never dropped

#### Scenario: Two calls of one kind in a day
- **WHEN** a call costing 0.30 and one costing 0.05 are metered the same day
- **THEN** the line sums them to 0.35 and its `maxCallCostUsd` is 0.30

#### Scenario: A line written before the maximum was kept
- **WHEN** a call is metered into a line without `maxCallCostUsd`
- **THEN** the line reads and its `maxCallCostUsd` is that call's cost

### Requirement: A stop on a full window arms one wake
With `usageLimits.wakeOnReset: true` (default false, and off when `usageLimits.enabled` is false), a usage-limit stop SHALL
read the limits once. Where a window is at its limit with a known reset, it SHALL arm one wake at the latest such
reset (`SessionWakes`, kept beside `state.json`).

#### Scenario: The window is full
- **WHEN** a session stops on a limit while the 5h window reads 100% and resets at 15:00
- **THEN** a wake is armed for 15:00

#### Scenario: No window is full
- **WHEN** a session stops on a limit while every window reads below 100%, or a full one reports no reset
- **THEN** no wake is armed and the session shows as stopped

### Requirement: A wake says one line, once
At its time a wake SHALL say one line through `SessionHost.nudgeTaskWindow` and nothing else. It is taken from the file
before it is sent, so a stop is woken at most once, across a restart. A report of the session working again SHALL
cancel it.

#### Scenario: The reset passes
- **WHEN** the reset time passes with a wake pending
- **THEN** one line is said to the session, and no later run says it again

#### Scenario: The human has an unsent draft
- **WHEN** the reset passes while the window holds an unsent draft
- **THEN** the wake stays pending and is retried on the next run, never typed onto the draft

#### Scenario: The session moves first
- **WHEN** the session or a human starts a turn before the reset
- **THEN** the wake is cancelled

#### Scenario: A restart between the stop and the reset
- **WHEN** jagt restarts with a wake pending
- **THEN** the wake is still due at the reset

### Requirement: A headless run has a spend ceiling
Every headless run SHALL be held to `orchestrator.assistant.maxCostUsd`, 5.00 unless set. A run that reaches it SHALL
come back as a failure naming the ceiling, its spend booked as for any run.

#### Scenario: A run under the ceiling
- **WHEN** a headless read costs less than `maxCostUsd`
- **THEN** it answers as before

#### Scenario: A run reaches the ceiling
- **WHEN** a headless read reaches `maxCostUsd` before it answers
- **THEN** the read fails naming the spend ceiling and no answer is read from it

### Requirement: A usage-limit stop is a typed report
A turn the CLI ends on a usage limit SHALL be reported by its own hook event. `hooks/<runtime>.properties` SHALL
declare it as `rate-limit-stop` with its `rate-limit-matcher`, a POST to `/api/agent/session/rate-limit`.
jagt SHALL never infer a limit from what a window shows.

#### Scenario: The CLI ends a turn on a rate limit
- **WHEN** the CLI fires its failure event with the rate-limit cause
- **THEN** the session is recorded as stopped, as for any ended turn

#### Scenario: A runtime declares no such event
- **WHEN** `hooks/<runtime>.properties` has no `rate-limit-stop`
- **THEN** no hook is written for it
