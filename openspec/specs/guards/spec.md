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

### Requirement: A record store packs its older half
`finished.jsonl` past 10 MB and `artifacts/` past 1000 task directories SHALL pack their older half beside them.
The packs are `finished-<stamp>.jsonl.gz` and `artifacts/archive-<stamp>.zip`; the newer half stays where every reader looks.
An artifact past 10 MB SHALL NOT be copied; `<name>.not-kept` names its size.

#### Scenario: The finished log passes its ceiling
- **WHEN** `done` appends a line past 10 MB
- **THEN** the older half is packed beside it and the `finished` report names the archive
- **AND** an intake still sees the newer half's ids

#### Scenario: The artifacts store is full
- **WHEN** `done` leaves the 1000th task directory
- **THEN** the older 500 are zipped into one archive and removed

#### Scenario: A runaway document
- **WHEN** a finished task's `plan.md` is 12 MB
- **THEN** its artifacts hold `plan.md.not-kept` instead

### Requirement: A log keeps one archive and a headless read none
`jagt-backend.log` and `jagt-master.log` SHALL roll at 10 MB and at each start into one `.1`, replacing the last. A headless read SHALL
keep no transcript (`--no-session-persistence`); a session's own transcripts are bounded by its agent CLI.

#### Scenario: A long run
- **WHEN** the Master's log passes 10 MB
- **THEN** it becomes `jagt-master.log.1` and the master window keeps following the new file

#### Scenario: A headless read ends
- **WHEN** a ticket read returns
- **THEN** nothing it said is left under the agent CLI's transcripts

### Requirement: A session writes only inside its task
A session's shell SHALL write only in the task's worktrees and the git directories they commit to.
It MAY also write `/tmp`, the build and cluster tools' own directories and `orchestrator.agent.sandbox.allowWrite`
(`WorktreeSandbox`). Every host SHALL stay reachable. A command needing more SHALL ask the human through the agent CLI's own prompt.

#### Scenario: A write outside the task
- **WHEN** ABC-42's session runs `rm -rf ~/other-project`
- **THEN** the OS refuses it

#### Scenario: A remote over ssh
- **WHEN** the session runs `git push` or a command `sandbox.excludedCommands` names
- **THEN** it runs outside the sandbox: Claude's sandbox carries the web alone

#### Scenario: The human widens it
- **WHEN** the session asks to run a command outside the sandbox
- **THEN** the card waits on the human, who allows it in the session's window

#### Scenario: Turned off
- **WHEN** `sandbox.enabled` is `false`
- **THEN** a session writes wherever the agent CLI's own rules let it

#### Scenario: Linux without the sandbox's tools
- **WHEN** jagt starts on Linux with Claude, the sandbox on, and `bwrap` or `socat` missing
- **THEN** the start is refused, naming the packages and `sandbox.enabled: false`
