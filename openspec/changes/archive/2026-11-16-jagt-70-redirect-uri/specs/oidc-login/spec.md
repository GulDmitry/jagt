## ADDED Requirements

### Requirement: The provider returns to the public URL
The redirect URI sent to the provider SHALL be the board's public address, `server.publicUrl`, followed by `/login/oauth2/code/oidc`.
#### Scenario: Login behind an ingress
- **WHEN** `server.publicUrl` is `https://jagt.example.org` and a login starts
- **THEN** the provider is asked to return to `https://jagt.example.org/login/oauth2/code/oidc`
