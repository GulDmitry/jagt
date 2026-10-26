## ADDED Requirements

### Requirement: A tree is verified once
A verification run SHALL be keyed on the worktree's tree state and the declared probes (`Verification.key`).
The last failing run's key is kept on the turn's marks. A question or permission request MUST NOT be verified.

#### Scenario: A question is not verified
- **WHEN** a session reports a question or a permission request
- **THEN** it does not land at `VERIFYING`, and a question standing there runs no probe and relays nothing

#### Scenario: An unchanged tree is not verified twice
- **WHEN** a task is at `VERIFYING` with the tree state and probes of its last failing run
- **THEN** nothing runs and nothing is relayed; the task lands `REVIEW_PENDING`, its message saying the failure stands
