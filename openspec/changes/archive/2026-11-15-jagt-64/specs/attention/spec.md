## MODIFIED Requirements

### Requirement: Reaching a session
`focus <task>` SHALL select the agent's window and raise the viewer, or say why not. The agent MUST live in tmux: a closed viewer stops nothing;
after a backend restart the next call reaches the new process. The board SHALL stay local unless `orchestrator.auth` is `password`
([board](../board/spec.md)): it can deploy. A worktree is briefed once, at creation; one older than a brief change keeps the old wording.
#### Scenario: The viewer was closed, or a brief changed
- **WHEN** you press Focus on a closed viewer
- **THEN** another viewer opens
- **WHEN** a session uses old wording
- **THEN** answer in the window, or recreate the task
