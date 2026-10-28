## MODIFIED Requirements

### Requirement: Plan first is a gate jagt holds
A task opened or reopened in `plan` mode, or with `agent.autoplan.enabled` on, SHALL hold its plan gate closed (`TaskState.planGate`):
a session's write to any file but `plan.md`, `task_visual.md` and `task_notes.md` is refused (`PlanFirst`).
`approve-plan` at `PLAN_PENDING`, and the Master's `go` on a gate autoplan closed, SHALL open it and start the session.

#### Scenario: A ratified probe is replaced
- **WHEN** a session at `IN_PROGRESS` reports `PLAN_PENDING` with a new probes block
- **THEN** the probes are read anew and the plan waits for approval
- **AND** from `VERIFYING` the report is refused, naming `IN_PROGRESS` first
