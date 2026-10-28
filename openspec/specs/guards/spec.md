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
When `orchestrator.agent.sandbox.enabled` is `true`, a session's shell SHALL write only in the task's worktrees
and the git directories they commit to.
It MAY also write `/tmp`, the user's temp directory, the build and cluster tools' own directories and `orchestrator.agent.sandbox.allowWrite`
(`WorktreeSandbox`). A command needing more SHALL ask the human through the agent CLI's own prompt.
By default the sandbox SHALL be off and a session SHALL reach every host.

#### Scenario: Default
- **WHEN** `sandbox.enabled` is unset
- **THEN** a session's Gradle reaches Maven Central
- **AND** a session writes wherever the agent CLI's own rules let it

#### Scenario: A write outside the task
- **WHEN** `sandbox.enabled` is `true` and ABC-42's session runs `rm -rf ~/other-project`
- **THEN** the OS refuses it

#### Scenario: A test mocking through an agent
- **WHEN** `sandbox.enabled` is `true` and a test in the session or in jagt's check attaches an agent to its own JVM
- **THEN** it attaches: the JVM's attach socket lies in the user's temp directory, which the sandbox lets it write

#### Scenario: A remote over ssh
- **WHEN** `sandbox.enabled` is `true` and the session runs `git push` or a command `sandbox.excludedCommands` names
- **THEN** it runs outside the sandbox: Claude's sandbox carries the web alone

#### Scenario: The human widens it
- **WHEN** `sandbox.enabled` is `true` and the session asks to run a command outside the sandbox
- **THEN** the card waits on the human, who allows it in the session's window

#### Scenario: A session cuts its own worktree
- **WHEN** ABC-42's session runs `git -C ~/repos/other worktree add /wt/other -b ABC-42 origin/release`
- **THEN** `ToolGate` refuses it, naming `update_agent_status` with `outcome=question` and `needsProject`
- **AND** `git worktree list` is still allowed

#### Scenario: Linux without the sandbox's tools
- **WHEN** jagt starts on Linux with Claude, `sandbox.enabled` `true`, and `bwrap` or `socat` missing
- **THEN** the start is refused, naming the packages and `sandbox.enabled: false`

### Requirement: The finished log packs its older half
`finished.jsonl` past 10 MB SHALL pack its older half beside it as `finished-<stamp>.jsonl.gz`; the newer half stays where
every reader looks.

#### Scenario: The finished log passes its ceiling
- **WHEN** `done` appends a line past 10 MB
- **THEN** the older half is packed beside it and the `finished` report names the archive
- **AND** an intake still sees the newer half's ids

### Requirement: A done leaves one snapshot
Each `done` SHALL write `archive/<yyMMdd-HHmmss>-<task>/` beside `state.json`: the task's documents and `finished.json`,
the pretty-printed finished record, which holds `usage`, `agentUsage` and the paths of the session logs as `transcripts`.
The logs are not copied. A document past 10 MB SHALL NOT be copied; `<name>.not-kept` names its size.
A failure writing a snapshot is logged and never refuses `done`.

#### Scenario: A task done twice in one day
- **WHEN** ABC-42 is done at 09:00 and again at 15:00 the same day
- **THEN** `archive/` holds `<day>-090000-ABC-42` and `<day>-150000-ABC-42`, each with its own documents

#### Scenario: A finished record with its budgets
- **WHEN** a task with its own usage and agent-session usage is done
- **THEN** its snapshot holds its documents and a `finished.json` carrying its id, `usage`, `agentUsage` and `transcripts`

#### Scenario: A runaway document
- **WHEN** a finished task's `plan.md` is 12 MB
- **THEN** its snapshot holds `plan.md.not-kept` instead

### Requirement: The archive is bounded by bytes
After each snapshot, while `archive/` holds more than `archive.maxMegabytes` of `jagt.yml` (default 20), the oldest entry
by name SHALL be deleted, an older `archive-*.zip` first. The snapshot just written is never deleted.

#### Scenario: The archive passes its budget
- **WHEN** a snapshot takes `archive/` past `archive.maxMegabytes`
- **THEN** the oldest entries are deleted until it fits, and the newest snapshot stays
