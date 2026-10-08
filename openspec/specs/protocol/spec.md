# protocol Specification

## Purpose

Everything crossing into jagt is a message, validated once at the door.

## Requirements

### Requirement: The protocol lives in core
`protocol/` in `core/` SHALL hold the wire record, its rules and the types the rest of jagt works in. It speaks the
centre's vocabulary; the centre does not know it (`RingsTest`). What a message cannot carry is not the
protocol's: a worktree claim is measured where the worktree is (`service/HandBack`), and which status it may land on is
`flow/FlowRules`'s ([task-flow](../task-flow/spec.md)).

#### Scenario: A round claims no diff
- **WHEN** a message says nothing changed
- **THEN** `service/HandBack` measures the worktree instead of believing it

### Requirement: No raw wire value travels inward
A message SHALL arrive as the strings and maps it was written in: `violations(...)` judges it, `accepted(...)` returns it
in jagt's own types or nothing. Which status, what a round claims, which request it is about and what the human is shown
are resolved once, here.

#### Scenario: Past the door
- **WHEN** code needs a message's status
- **THEN** it reads the accepted type, never the wire

### Requirement: Arguments are read into the message
`MessageTool` SHALL be the one path from the wire to a verb: the arguments are read into the message and judged, and only
then does the tool run. A field the message does not declare is ignored, so a CLI a version ahead is not rejected.

#### Scenario: An unknown field
- **WHEN** a call carries a field the message does not declare
- **THEN** the field is ignored and the call is judged on the rest

### Requirement: Field and consistency rules, one report
Field rules SHALL catch a value out of its enum, a missing required field, a link nobody can open. Consistency rules
catch two fields that cannot both hold: `reviewRequests` beside `reviewRequestUrl`, CI_POLLING with no request anywhere
in the message, a request filed under a project the task does not have.

#### Scenario: CI_POLLING without a request
- **WHEN** a message reports CI_POLLING and names no request
- **THEN** it is refused as inconsistent

### Requirement: Every violation at once
Validation SHALL report every violation at once, never first-failure: one error per call is one call per error. Each
names the field and what was expected; a missing required field is named with the rest, not first and alone. `startup/StartupValidation` refuses a bad install the same way.

#### Scenario: Several wrong fields
- **WHEN** a required field is missing and another is out of its enum
- **THEN** one refusal names both, each with what was expected

### Requirement: The shape and the tool are declared once
`protocol/Schema` SHALL render what a caller is given out of the fields a message declares. An enum comes from whatever
enumerates it (`FlowRules.reportable`), never a list beside it. `McpToolRegistry` has one way to declare a tool, taking a message class, so a tool skipping validation
does not compile.

#### Scenario: A status is added
- **WHEN** a session may report a new status
- **THEN** the schema offers it with no other edit

### Requirement: A session's refusal is a correction
A refusal SHALL come back from the session's call, never a block or a human's question. `surface/mcp/ToolFailure` reads
what comes next off what the handler threw: `validation` is fixed and resent; `business` (a `flow/Refusal`, a vanished
task) and `permission` are the answer; `transient` alone is `retryable`.

#### Scenario: Refused
- **WHEN** a session's call is refused as `validation`
- **THEN** it fixes every line and calls again

### Requirement: A hook's post is filtered, not refused
`protocol/SessionHookReport` SHALL accept what a hook posts without correction, since the hook throws the answer away:
jagt drops what it cannot believe, keeps the rest, and logs once what it dropped.

#### Scenario: A relative log path
- **WHEN** a hook posts a relative log path
- **THEN** the path is dropped and the rest kept

### Requirement: A paid read is judged and asked again
For a paid read jagt is the sender: `protocol/TicketRead` SHALL judge the answer, and its violations ride into the next
ask, because the identical question returns the identical answer.

#### Scenario: A false "does not exist"
- **WHEN** the read says a ticket that plainly exists does not
- **THEN** it is asked again, up to three times, each attempt told what the last got wrong

#### Scenario: The wrong ticket
- **WHEN** the read answers about a different key, or with no key or link
- **THEN** no task opens, and the message says why

#### Scenario: Prose instead of the object
- **WHEN** a paid call answers in prose, not the schema's object
- **THEN** it is asked once more at once, both calls billed; a failed call is not

### Requirement: Retries are bounded and end in a person
`protocol/RetryPolicy` SHALL allow a paid read three attempts, spaced, under a budget. An exhausted policy answers with
no facts, never a guess; reaching the human is the caller's. A round nobody could read taps the human once
(`AutoReviewScheduler`).

#### Scenario: An unattended poll exhausts
- **WHEN** the auto-review poll cannot read a round
- **THEN** the human is tapped once, not left a log line

### Requirement: A ticket is read through the human's MCP servers
A ticket ref SHALL be read, paid, through the human's own MCP servers for title, labels and project.
It SHALL load no built-in tool and no MCP write.
`assistant.mcp-config`, a path or the JSON itself, loads only the declared servers.
Declared servers have no plugin prefix in their tool names, so `allowed-tools`, if set, must be rewritten.

#### Scenario: Key or URL
- **WHEN** the human runs `do ABC-42` or `do <url>`
- **THEN** title, labels and project are read through those servers

#### Scenario: No server reaches the tracker
- **WHEN** the human runs `do ABC-42 <project>` and no MCP server reaches the tracker
- **THEN** the read fails naming what stopped it, and the task carries no title

#### Scenario: No summary
- **WHEN** the item has no summary
- **THEN** a short title comes from the description; a link is never invented

### Requirement: Tracker stages open and close tasks
Off by default, a tracker stage SHALL open or close a task with no human action (`tracker`).

#### Scenario: Start and landed stages
- **WHEN** an item reaches the start stage, then the landed stage
- **THEN** its task opens itself, then closes itself, its work having left the worktree

### Requirement: An MCP caller is a task or the Master, never assumed
A call whose `X-Working-Directory` lies in a task's worktree SHALL be that task's. Else it SHALL present the root's
`.jagt/master-token`, drawn at each start (`surface/mcp/MasterToken`), to be the Master's, or be refused 401.

#### Scenario: Nobody in particular
- **WHEN** a tokenless `curl`, or a worktree no task holds, calls
- **THEN** it is refused, never promoted to the Master

#### Scenario: What a sub-agent sees
- **WHEN** a sub-agent lists or calls tools
- **THEN** it has `update_agent_status`, `notify_user` and `open_in_ide`; every other tool is the Master's alone
