## MODIFIED Requirements

### Requirement: You pause a started task
`pause` SHALL stop a started task for you to look at: nothing of jagt runs on it, reads included, until your `lift`.
It is offered from IN_PROGRESS on, never to a stopped task, and is the human's in every Master mode.
You may say it to the Master instead: its `pause_task` pauses the task on your word alone, with your reason on the stop,
under the same offer. The task's session keeps running; jagt says nothing into it and moves nothing. The card carries
the stop mark. A `deploy` you press lifts it as it lifts any stop. Nothing else ends it.

#### Scenario: A paused task is left alone
- **WHEN** you press `pause` on ABC-42 at IN_PROGRESS
- **THEN** no job reads or moves it and nothing is relayed into its session until you press `lift`
- **AND** the stop is neither logged as a warning nor notified

#### Scenario: Where pause is offered
- **WHEN** ABC-42 is NEW, PLAN_PENDING, DONE or already stopped
- **THEN** `pause` is not offered

#### Scenario: The Master cannot press pause
- **WHEN** the Master presses `pause`
- **THEN** it is refused

#### Scenario: The Master pauses on your word
- **WHEN** you tell the Master to pause ABC-42 at IN_PROGRESS "until the managers answer"
- **THEN** its `pause_task` stops ABC-42 as your `pause` does, the stop reading "until the managers answer"
- **AND** where `pause` is not offered, `pause_task` is refused and nothing is stopped
