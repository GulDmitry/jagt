## ADDED Requirements

### Requirement: A link names the board's public URL
A link jagt sends to a human (banner, channel message, startup banner) SHALL start with `server.publicUrl` when it is set,
else with `http://localhost:<port>`.
The URLs a session reaches jagt by (mcp, hook, gate) SHALL stay loopback.

#### Scenario: A public URL is set
- **WHEN** `server.publicUrl` is `https://jagt.example.org` and a notification names `ABC-42`
- **THEN** its link is `https://jagt.example.org/?task=ABC-42`

#### Scenario: No public URL
- **WHEN** `server.publicUrl` is blank
- **THEN** links name `http://localhost:<port>` as before
