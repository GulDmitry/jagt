## MODIFIED Requirements

### Requirement: What jagt answers a hook, the harness reads
The hook SHALL print jagt's answer. Three hooks are answered: `gate=PreToolUse`, scoped to the shell tool and the
file-writing tools (it holds writes while a plan waits, [task-flow](../task-flow/spec.md)); `turn-end=Stop`; and
`prompt-gate=UserPromptSubmit`, answered by [cold-prompts](../cold-prompts/spec.md).
A session is never launched in its CLI's own plan mode. `turn-end` refuses once a turn that leaves the agent's move
unreported (`Move.endsUnreported`), and shows the human alone a round handed to the Master (`AgentRuntime.toldTheHuman`).

#### Scenario: jagt is unreachable
- **WHEN** `gate`, `turn-end` or `prompt-gate` cannot reach jagt
- **THEN** none refuses anything
