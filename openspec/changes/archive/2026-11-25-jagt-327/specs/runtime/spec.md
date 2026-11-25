## MODIFIED Requirements

### Requirement: The backend owns no terminal
The backend SHALL own no terminal, and no terminal UI SHALL come back. It MUST NOT automate a GUI or keystrokes. Agent terminals SHALL be windows in
a session host (`port/terminal/SessionHost`, tmux by default, herdr by key), one kitty window attached.

#### Scenario: An agent terminal opens
- **WHEN** jagt starts an agent session
- **THEN** it runs in a session-host window, viewed through the one attached kitty window
