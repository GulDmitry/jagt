## MODIFIED Requirements

### Requirement: What jagt answers a hook, the harness reads
The hook SHALL print jagt's answer. Two hooks are answered:
`gate=PreToolUse`, scoped to the shell tool and the file-writing tools (it holds writes while a plan waits,
[task-flow](../task-flow/spec.md)); and `turn-end=Stop`.
A session is never launched in its CLI's own plan mode. The latter refuses once a turn that leaves the
agent's move unreported (`Move.endsUnreported`), and shows the human alone a round handed to the Master
(`AgentRuntime.toldTheHuman`). After a compaction the start SHALL be answered with the task key, also its branch,
the title, the board status and the last report: the open question, else the status message.

#### Scenario: A session starts after compaction
- **WHEN** a start report follows a COMPACTION and the task holds `outcome=progress — v3 routes done`
- **THEN** the answer names its brief, the task as its branch and `your last report: outcome=progress — v3 routes done`
- **AND** every other report is answered empty

#### Scenario: jagt is unreachable
- **WHEN** `gate` or `turn-end` cannot reach jagt
- **THEN** neither refuses anything
