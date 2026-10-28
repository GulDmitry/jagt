## MODIFIED Requirements

### Requirement: What jagt answers a hook, the harness reads
The hook SHALL print jagt's answer. Three hooks are answered: `gate=PreToolUse`, scoped to the shell tool and the file-writing tools (it holds
writes while a plan waits, [task-flow](../task-flow/spec.md)); `turn-end=Stop`; and `prompt-gate=UserPromptSubmit`, answered by
[cold-prompts](../cold-prompts/spec.md). A session is never launched in its CLI's own plan mode. `turn-end` refuses once a turn that leaves the
agent's move unreported (`Move.endsUnreported`) or changed the worktree and reported nothing, at every status but REVERTED, DEPLOY_CONFLICT and
DONE (`TurnTree`); it shows the human alone a round handed to the Master (`AgentRuntime.toldTheHuman`).

#### Scenario: jagt is unreachable
- **WHEN** `gate`, `turn-end` or `prompt-gate` cannot reach jagt
- **THEN** none refuses anything

#### Scenario: A change typed in after the deploy
- **WHEN** a turn at DEPLOYED ends with the worktree changed and nothing reported
- **THEN** the end is refused once, naming IN_PROGRESS or reverting the change
- **AND** the second end on the same tree passes

#### Scenario: A deploy check changes nothing
- **WHEN** a turn at DEPLOYED ends with the worktree as the last end left it
- **THEN** the turn ends
