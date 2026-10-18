## ADDED Requirements

### Requirement: A session writes only inside its task
A session's shell SHALL write only in the task's worktrees, the git directories they commit to, `/tmp`, the build and
cluster tools' own directories and `orchestrator.agent.sandbox.allowWrite` (`WorktreeSandbox`). Every host SHALL stay
reachable. A command needing more SHALL ask the human through the agent CLI's own prompt.

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
