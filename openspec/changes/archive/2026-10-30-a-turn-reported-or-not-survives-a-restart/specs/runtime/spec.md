## MODIFIED Requirements

### Requirement: What jagt answers a hook, the harness reads
The hook SHALL print jagt's answer. Three hooks are answered: `gate=PreToolUse`, scoped to the shell tool and the file-writing tools (it holds
writes while a plan waits, [task-flow](../task-flow/spec.md)); `turn-end=Stop`; and `prompt-gate=UserPromptSubmit`, answered by
[cold-prompts](../cold-prompts/spec.md). A session is never launched in its CLI's own plan mode. `turn-end` refuses once a turn that leaves the
agent's move unreported (`Move.endsUnreported`); reported means `update_agent_status` since the turn began, recorded on the task,
so a read call is none and a restart changes nothing. It also refuses once a turn that changed the tree and reported nothing
(`TurnTree`), at every status but REVERTED, DEPLOY_CONFLICT and DONE. It shows the human alone a round handed to the Master (`AgentRuntime.toldTheHuman`).

#### Scenario: jagt is unreachable
- **WHEN** `gate`, `turn-end` or `prompt-gate` cannot reach jagt
- **THEN** none refuses anything

#### Scenario: A change typed in after the deploy
- **WHEN** a turn at DEPLOYED ends with the tree changed and nothing reported
- **THEN** the end is refused once, and the second end on the same tree passes
- **WHEN** a turn at DEPLOYED ends with the tree as the last end left it
- **THEN** the turn ends

#### Scenario: Only a read call
- **WHEN** a turn at IN_PROGRESS began, called `list_tasks` and ends, before or after a restart
- **THEN** the end is refused once
