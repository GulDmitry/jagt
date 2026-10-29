## ADDED Requirements


### Requirement: A session waits out a dependency that is down
A session whose dependency is down SHALL report IN_PROGRESS with `outcome=wait`, its message naming what is down and how to probe it.
The turn SHALL stay the session's and nothing SHALL ask the human. After each wait of `infraWait.backoff` jagt SHALL say `wait-over`.
Once the session answered `wait-over` with a wait as many times as there are waits, the task SHALL stop as `WAIT`.
`outcome=wait` SHALL be refused where `infraWait.backoff` is `[]`.

#### Scenario: A session waits
- **WHEN** ABC-42's session reports `outcome=wait`, "ledger db down: ping ledger.internal"
- **THEN** its status chip's hover says what is down and `next try HH:MM UTC`, and nobody is asked
- **AND** after the first wait jagt says `wait-over`; the session probes, and goes on, or reports `wait` again

#### Scenario: The dependency stays down
- **WHEN** the session answered `wait-over` with a wait 3 times
- **THEN** the task stops as `WAIT` with the session's last message as evidence

#### Scenario: Somebody else speaks first
- **WHEN** you type to the session, or a relay or an answer opens a new turn, before the wait is over
- **THEN** the wake of the old turn is never due

#### Scenario: A restart mid-wait
- **WHEN** jagt restarts while a wake is pending
- **THEN** the wake stands, and a `restarted` answered with a wait counts nothing
