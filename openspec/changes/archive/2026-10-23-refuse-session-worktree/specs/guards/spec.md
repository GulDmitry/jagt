## MODIFIED Requirements

### Requirement: A session writes only inside its task
A session's shell SHALL write only in the task's worktrees and the git directories they commit to.
It MAY also write `/tmp`, the user's temp directory, the build and cluster tools' own directories and `orchestrator.agent.sandbox.allowWrite`
(`WorktreeSandbox`). Every host SHALL stay reachable. A command needing more SHALL ask the human through the agent CLI's own prompt.

#### Scenario: A write outside the task
- **WHEN** ABC-42's session runs `rm -rf ~/other-project`
- **THEN** the OS refuses it

#### Scenario: A test mocking through an agent
- **WHEN** a test in the session or in jagt's check attaches an agent to its own JVM
- **THEN** it attaches: the JVM's attach socket lies in the user's temp directory, which the sandbox lets it write

#### Scenario: A remote over ssh
- **WHEN** the session runs `git push` or a command `sandbox.excludedCommands` names
- **THEN** it runs outside the sandbox: Claude's sandbox carries the web alone

#### Scenario: The human widens it
- **WHEN** the session asks to run a command outside the sandbox
- **THEN** the card waits on the human, who allows it in the session's window

#### Scenario: Turned off
- **WHEN** `sandbox.enabled` is `false`
- **THEN** a session writes wherever the agent CLI's own rules let it

#### Scenario: A session cuts its own worktree
- **WHEN** ABC-42's session runs `git -C ~/repos/other worktree add /wt/other -b ABC-42 origin/release`
- **THEN** `ToolGate` refuses it, naming `update_agent_status` with `outcome=question` and `needsProject`
- **AND** `git worktree list` is still allowed

#### Scenario: Linux without the sandbox's tools
- **WHEN** jagt starts on Linux with Claude, the sandbox on, and `bwrap` or `socat` missing
- **THEN** the start is refused, naming the packages and `sandbox.enabled: false`
