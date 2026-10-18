# guards Specification

## Purpose

What a guard's refusal leaves on its task, so a gap in `docs/rules/guards.md` reopens on a number.

## Requirements

### Requirement: A refusal is counted on its task
A session line `ToolGate` refuses and a report `FlowRules.refusedReport` refuses SHALL each add one to the task's
`refusals`, per `task/Guard`. `finished.jsonl` SHALL carry the count, and `finished` SHALL sum it per kind.

#### Scenario: A push to another branch
- **WHEN** ABC-42's session runs `git push origin dev`
- **THEN** it is refused, and ABC-42's `refusals` count one `session line`

#### Scenario: The finished report
- **WHEN** the human runs `finished`
- **THEN** its last line sums the refusals of every finished task per kind

### Requirement: The press log rolls at one megabyte
`interactions.jsonl` past 1 MB SHALL roll into one `interactions.jsonl.1`, replacing the last; the page holds at most
200 unsent presses.

#### Scenario: A long week of presses
- **WHEN** a batch takes the log past 1 MB
- **THEN** it becomes `interactions.jsonl.1`, the older `.1` is gone, and the next press starts a new file
