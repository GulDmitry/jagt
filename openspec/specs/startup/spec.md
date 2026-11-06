# startup Specification

## Purpose

What a start of jagt checks and refuses, where its configuration lives, and which file every agent CLI reads.

## Requirements

### Requirement: A bad install is refused at once, offline
A start SHALL refuse a missing or wrong setup with **every** problem at once, each naming the key that fixes it; a
tool only tidying up after a task (`lsof`, `pkill`) missing is said once and starts anyway. It checks nothing over
the network; `--orchestrator.startup-checks=false` skips the checks about the machine, never those of jagt's own files.

#### Scenario: Missing or wrong setup
- **WHEN** something the setup needs is missing or wrong, such as an `agent.cli` no agent CLI answers to, and you start jagt
- **THEN** it is refused with every problem at once, each naming the key that fixes it, and an unknown `agent.cli` its value

#### Scenario: Wrong token or unreachable host
- **WHEN** a token is wrong or the host unreachable, and you start jagt
- **THEN** it is not detected: a start checks nothing over the network

#### Scenario: No desktop
- **WHEN** a suite boots on a machine with no desktop
- **AND** you pass `--orchestrator.startup-checks=false`
- **THEN** every check of the machine is skipped

#### Scenario: Checks off, cases missing
- **WHEN** you start jagt with the checks off and no case file in its root
- **THEN** it is refused, naming the steps no case sends

#### Scenario: The board port is held
- **WHEN** another jagt or process listens on the board port, and you start jagt
- **THEN** it exits at once with one line naming the port, and logs no ERROR and no stack

#### Scenario: Linux with the platform unset
- **WHEN** you start jagt on Linux with the platform left unset
- **THEN** it is refused: unset means macOS

### Requirement: Configuration lives in jagt.yml
Every setting SHALL live in `jagt.yml` at the repository root, copied from `jagt.yml.dist`, where every key is
described. A command-line flag outranks the file. `projects`, `viewer`, `agent`, `worktree`, `codeReview`,
`autoReview`, `master` and `tracker` SHALL be re-read on every access; `agent.cli`, `tracker.workflow` and every
other key need a restart.

#### Scenario: Where a setting goes
- **WHEN** you ask where a setting goes
- **THEN** `jagt.yml` at the repository root, copied from `jagt.yml.dist`

#### Scenario: A key had no effect
- **WHEN** a key you set had no effect
- **THEN** a command-line flag outranks the file, or the key is outside the live sections and needs a restart

#### Scenario: Changing the Master's mode
- **WHEN** you edit `roles.master.mode` while jagt runs
- **THEN** the next read of it uses the new mode, with no restart

#### Scenario: Legacy config.json
- **WHEN** jagt refuses to start over `config.json`
- **THEN** it is no longer read, and the refusal prints the `jagt.yml` to write instead

### Requirement: Every agent CLI reads the same rules
Whoever works **on** jagt, with any agent CLI, SHALL read the same `AGENTS.md` and reach the same MCP server.
See [components](../../../docs/rules/components.md#whoever-works-on-jagt-reads-the-same-file-and-reaches-the-same-server).

#### Scenario: Another agent CLI
- **WHEN** you open the repository with another agent CLI
- **THEN** it gets the same rules and the same MCP server

### Requirement: OpenCode may run the agents
`orchestrator.agent.cli: opencode` SHALL run every session on OpenCode, reaching jagt as a remote MCP server carrying the
task's worktree as its caller. Its config SHALL be jagt's own; the project's `opencode.json` neither replaces nor feeds it.
`orchestrator.opencode.endpoint` and `model`, when set, SHALL be the sessions' model; blank, the user's own OpenCode config
chooses. Startup SHALL refuse it with `agent.sandbox.enabled`, an endpoint that is no http(s) address, or one without a
model.

#### Scenario: A local model
- **WHEN** `endpoint` and `model` are set under `orchestrator.opencode`
- **THEN** each session starts on that model

#### Scenario: A plan
- **WHEN** a task starts in plan mode
- **THEN** its session starts on OpenCode's plan agent

#### Scenario: A sandbox
- **WHEN** `agent.cli` is `opencode` and `agent.sandbox.enabled` is true
- **THEN** the start is refused, naming the key

### Requirement: The steps the Master leaves you are checked at start
`roles.master.mine` SHALL name only `task/MasterRight` steps, and in `act` `deploy` and `revert` both or neither; anything
else SHALL refuse the start (`startup/MasterCheck`).

#### Scenario: A misspelled step
- **WHEN** `roles.master.mine` names a step `task/MasterRight` lacks
- **THEN** the start is refused, naming the steps there are

#### Scenario: Keeping deploy but not revert
- **WHEN** `roles.master.mine` names `deploy` without `revert` in `roles.master.mode: act`
- **THEN** the start is refused: both write the same shared branch

### Requirement: The Master is configured under its role
The Master SHALL be configured under `orchestrator.roles.master`, with `mode`, `model`, `brief` and `mine`. The old
`orchestrator.master` key SHALL still be read; `roles.master` wins field by field and the old key fills each field
it leaves unwritten (`roles.qa` likewise over `qa`). Reading the old key logs one
deprecation line and is never a startup problem.

#### Scenario: Only the old key
- **WHEN** `jagt.yml` writes `master: {mode: act}` and no `roles.master`
- **THEN** the Master acts, and one deprecation line is logged

#### Scenario: Both keys
- **WHEN** `jagt.yml` writes `master: {mode: act}` and `roles: {master: {mode: judge}}`
- **THEN** the Master judges

#### Scenario: A partial role key
- **WHEN** `jagt.yml` writes `master: {mine: [plan, deploy, revert]}` and `roles: {master: {mode: act}}`
- **THEN** the Master acts and `mine` stays `[plan, deploy, revert]`

### Requirement: A read that needs no tool may ask a local model
Where `orchestrator.assistant.local.endpoint` is set, a read of a kind in `orchestrator.assistant.local.kinds`
(`COMMAND_MAP` unless set) SHALL go to that OpenAI-compatible endpoint. The read's schema is sent as its response format.
A local read that fails, times out or answers outside the schema SHALL be asked of the cloud once, and a WARN says why.
With no endpoint set every read goes to the cloud.

#### Scenario: A local model is configured
- **WHEN** an endpoint is set and the operator types a request jagt maps onto a command
- **THEN** the read goes to the endpoint and its answer is the mapping

#### Scenario: The endpoint fails or is too slow
- **WHEN** the endpoint refuses the connection, answers with an error or outlasts `timeout`
- **THEN** the cloud read answers the request and a WARN line names the cause

#### Scenario: A kind that is not listed
- **WHEN** a read of a kind outside `kinds` is asked
- **THEN** it goes to the cloud

#### Scenario: A misconfigured local model
- **WHEN** the endpoint is set and `model` is blank, the endpoint is not http(s), or `kinds` lists a kind that reads tools, such as `TICKET_READ`
- **THEN** the start is refused naming `orchestrator.assistant.local.model`, `.endpoint` or `.kinds`

### Requirement: A role may read on OpenCode
`orchestrator.opencode.reads` SHALL map a role id (or `master`, the round reads) to the model its read runs on, a blank
value meaning `orchestrator.opencode.model`. A role not named reads as before. Startup SHALL refuse a key that is neither
`master` nor an installed role, `reads` without `orchestrator.opencode.endpoint`, and a role resolving to no model.
It SHALL also refuse the binary missing from PATH wherever a read or the sessions run on OpenCode.

#### Scenario: An unknown role
- **WHEN** `reads` names `qaa` and no installed role has that id
- **THEN** the start is refused, naming `orchestrator.opencode.reads`

#### Scenario: Reads without an endpoint
- **WHEN** `reads` names `qa` and `endpoint` is blank
- **THEN** the start is refused, naming `orchestrator.opencode.endpoint`, so no read falls to the user's own provider

#### Scenario: A role with no model
- **WHEN** `reads` names `master` with a blank value and `model` is blank
- **THEN** the start is refused, naming `orchestrator.opencode.reads`

#### Scenario: A role reads on the endpoint
- **WHEN** `reads` is `{qa: qwen-4b}` with an endpoint set
- **THEN** the start is accepted while the sessions stay on the default agent CLI

### Requirement: A read named under reads runs on OpenCode
A role named under `orchestrator.opencode.reads` SHALL have its read run as `opencode run` on the endpoint, on its model.
The QA checker read follows the role's declared tools, `master` names the round reads, and a role not named reads on `claude -p`.

#### Scenario: A named role
- **WHEN** `reads` names `qa` and ABC-42's QA check opens
- **THEN** the read runs on OpenCode on the model named for `qa`
- **AND** a fenced JSON answer is read as the check's verdict

#### Scenario: A role not named
- **WHEN** `reads` does not name `master`
- **THEN** the round reads run on `claude -p`

### Requirement: A read on OpenCode is held to its role's scope
A read SHALL be offered `read`, `grep`, `glob`, `bash` and, where the role fetches, `webfetch`, and no MCP server.
Its permissions SHALL deny every command first, allow the role's, deny its refused ones, and deny any redirect last.
Every call SHALL first go to jagt's read gate through a plugin written beside `state.json` at each start.
A refused call or an unreachable gate denies the call, and the model retries.

#### Scenario: A call outside the scope
- **WHEN** a read's `git log > f` is put to the gate, or its `touch b.txt` was denied by the permissions
- **THEN** the call does not run, and the read goes on to an answer that stands

#### Scenario: The plugin never loaded
- **WHEN** a call completed and no call reached the gate
- **THEN** the answer is discarded and the check reports that it failed
- **AND** a completed read of a file the role refuses is discarded and answers failed the same way

#### Scenario: Tokens and the breaker
- **WHEN** a read completes, or a local read fails
- **THEN** its tokens are booked whatever it answered, and the cloud's breaker stays closed

### Requirement: A local endpoint is read for its model
`orchestrator.opencode.endpoint` and `orchestrator.assistant.local.endpoint`, when set, SHALL each be read every minute
(`<endpoint>/models`); one not answering or not listing its `model` is jagt's lost reach on the board's head, not pressable.

#### Scenario: A local endpoint not serving its configured model is a lost reach until a read lists it
- **WHEN** ABC-42 runs and `assistant.local.model` is `qwen` while the endpoint lists only `other`
- **THEN** the head says `<endpoint> does not serve qwen`, no card stops, and a read listing `qwen` clears it
