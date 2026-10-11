## ADDED Requirements

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
