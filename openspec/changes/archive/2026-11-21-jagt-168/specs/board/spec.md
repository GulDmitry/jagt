## MODIFIED Requirements

### Requirement: A foreign request is refused
The refused-request door SHALL refuse a foreign Host or Origin unless the host is the `publicUrl`'s or in `auth.allowedHosts`.
It SHALL refuse a write without Origin but to `/mcp` and `/api/agent/`; the firewall SHALL reject a respelled path.
#### Scenario: Behind an ingress, or a stranger posts
- **WHEN** a request's Host is `server.publicUrl`'s or is in `auth.allowedHosts`
- **THEN** the refused-request door lets it through
- **WHEN** a foreign page, a rebound name, a session's `curl` or a respelled `/api;x/…` posts a verb
- **THEN** refused: 403, or 400 where the firewall rejects the spelling; `ToolGate` stops the `curl` first
