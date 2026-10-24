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
- **THEN** `service/HandBack` measures the worktree

### Requirement: No raw wire value travels inward
A message SHALL arrive as the strings and maps it was written in: `violations(...)` judges it, `accepted(...)` returns it
in jagt's own types or nothing. The status, the round's claim, its request and the human's line are resolved once, here.

#### Scenario: Past the door
- **WHEN** code needs a message's status
- **THEN** it reads the accepted type, never the wire

### Requirement: Arguments are read into the message
`MessageTool` SHALL be the one path from the wire to a verb: arguments are read into the message and judged before
the tool runs. A field the message does not declare is ignored, so a CLI a version ahead is not rejected; one
misspelling a field the call left out is refused.

#### Scenario: An unknown field
- **WHEN** a call carries an undeclared field
- **THEN** it is ignored and the rest judged

#### Scenario: A misspelled field
- **WHEN** a call sends `task_id` and no `taskId`
- **THEN** it is refused naming `taskId`, never run on the caller's own task

### Requirement: Field and consistency rules, one report
Field rules SHALL catch a value out of its enum, a missing required field, a link nobody can open. Consistency rules
catch two fields that cannot both hold: `reviewRequests` beside `reviewRequestUrl`, CI_POLLING with no request, a
request filed under a project the task does not have.

#### Scenario: CI_POLLING without a request
- **WHEN** a message reports CI_POLLING without one
- **THEN** it is refused as inconsistent

### Requirement: A session may declare that no plan comes first
`update_agent_status` SHALL take `outcome=no_plan` with status `IN_PROGRESS`, its message the reason; the report opens the
plan gate ([task-flow](../task-flow/spec.md)). For a task opened in `plan` mode it SHALL be a violation of `outcome`.

#### Scenario: Declared in plan mode
- **WHEN** a session of a `plan`-mode task reports `outcome=no_plan`
- **THEN** it is refused as a violation and the plan gate stays closed

### Requirement: A session may say only the human can unblock it
`update_agent_status` SHALL take `outcome=permission`: a question stopped on a call the CLI denied, recorded as the
task's `PERMISSION` report, which nothing standing in for the human answers ([master](../master/spec.md)).

#### Scenario: Edits denied in the deploy worktree
- **WHEN** a session reports `outcome=permission`
- **THEN** the card reads `NEEDS INPUT: permission: …` and the Master leaves it to you

### Requirement: Every violation at once
Validation SHALL report every violation at once, never first-failure, each naming the field and what was expected.
`startup/StartupValidation` refuses a bad install the same way.

#### Scenario: Several wrong fields
- **WHEN** a required field is missing and another is out of its enum
- **THEN** one refusal names both

### Requirement: The shape and the tool are declared once
`protocol/Schema` SHALL render what a caller is given out of the fields a message declares. An enum comes from
whatever enumerates it (`FlowRules.reportable`), never a list beside it. `McpToolRegistry` has one way to declare a
tool, taking a message class, so a tool skipping validation does not compile.

#### Scenario: A status is added
- **WHEN** a session may report a new status
- **THEN** the schema offers it with no other edit

### Requirement: A session's refusal is a correction
A refusal SHALL come back from the session's call, never a block or a human's question. `surface/mcp/ToolFailure` reads
what comes next off what the handler threw: `validation`, a wrong argument, is fixed and resent; `business` (a
`flow/Refusal`: what jagt holds refuses) and `permission` are the answer; `transient` alone is `retryable`.

#### Scenario: The board is full
- **WHEN** a session asks for a 25th task, or hands back owing its notes
- **THEN** it is refused as `business`, not `validation`

### Requirement: A hook's post is filtered, not refused
`protocol/SessionHookReport` SHALL accept what a hook posts without correction, since the hook throws the answer away:
what jagt cannot believe is dropped and logged once.

#### Scenario: A relative log path
- **WHEN** a hook posts one
- **THEN** it is dropped, the rest kept

### Requirement: A paid read is judged and asked again
For a paid read jagt is the sender: `protocol/TicketRead` SHALL judge the answer, and its violations ride into the next
ask: the identical question returns the identical answer.

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
no facts, never a guess; reaching the human is the caller's (`AutoReviewScheduler`).

#### Scenario: An unattended poll exhausts
- **WHEN** the auto-review poll cannot read a round
- **THEN** the human is tapped once, not left a log line

### Requirement: A ticket is read through the human's MCP servers
A ticket ref's title, labels and project SHALL be read, paid, through the human's MCP servers.
`assistant.mcp-config`, a path or the JSON itself, loads only the declared servers; their tool names carry no plugin prefix.

#### Scenario: Key or URL
- **WHEN** the human runs `do ABC-42` or `do <url>`
- **THEN** all three are read through those servers

### Requirement: A ticket read loads no built-in tool
A ticket read SHALL load no built-in tool. `ReadGate` SHALL refuse any call but MCP reads (`get*`, `list*`…, `*discussions*`,
`*notes*`, no `*_delete` mid-name) and `allowed-tools`. A bare server there means its reads, your allow rules
included; unreachable, every call. An answer that never reached it is discarded; a read it refused a tool is not asked again.

#### Scenario: A read named by what it returns
- **WHEN** a round's read calls `mr_discussions`, then a tool the gate refuses
- **THEN** the first runs, and the read fails once, never asked again for the same refusal

#### Scenario: No server reaches the tracker, or no summary
- **WHEN** no MCP server reaches the tracker of `do ABC-42 <project>`
- **THEN** the read fails naming what stopped it, and the task carries no title
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
- **THEN** it is refused, never the Master

#### Scenario: What a sub-agent sees
- **WHEN** a sub-agent lists or calls tools
- **THEN** it has `update_agent_status`, `notify_user` and `open_in_ide`; every other tool is the Master's alone

#### Scenario: A tool name nobody declared
- **WHEN** a caller names an unknown tool
- **THEN** the refusal lists its tools

### Requirement: A tool says whether it changes anything
`McpToolRegistry` SHALL take an `Effect` for every tool, and `tools/list` SHALL carry `readOnlyHint` from it. A read, or a
report to jagt, SHALL be `REPORTS`; every other tool SHALL be `CHANGES`.

#### Scenario: A session where only calls that change nothing go through
- **WHEN** a session runs where only calls that change nothing go through
- **THEN** it still reports its status and its question to the board

### Requirement: A session may ask for another repository
`update_agent_status` SHALL take `needsProject`, a key `list_projects` prints, with `outcome=question` alone. A key
that is not configured, or that the task already holds, SHALL be a violation naming the keys there are. jagt SHALL
record the request on the task (`TaskState.repoRequest`) until a repository is added or the session reports anything else.

#### Scenario: A session asks for a configured project
- **WHEN** a task in `sng` reports `outcome=question` with `needsProject: schemaRegistry`
- **THEN** the request is recorded on the task and the question reaches the board

#### Scenario: An unknown or held key
- **WHEN** `needsProject` names a project `jagt.yml` lacks, or one the task holds
- **THEN** it is refused naming the keys there are, and nothing is recorded

#### Scenario: Outside a question
- **WHEN** `needsProject` comes with any other outcome
- **THEN** it is refused as a violation of `outcome`

### Requirement: A request says why
`update_agent_status` and `initialize_task` SHALL require `why`, one sentence of at most 120 characters, kept on the
step the call writes; missing or longer, the call is refused as `validation` naming `why` and 120.

#### Scenario: A reason too long
- **WHEN** a session reports with a `why` of 121 characters
- **THEN** it is refused as `validation` naming `why` and 120, and nothing moves
