## MODIFIED Requirements

### Requirement: Only the allowed subject logs in through the provider
`orchestrator.auth.mode: oidc` SHALL guard the board as `password` does, the login being an OpenID Connect provider's,
discovered from `orchestrator.auth.oidc.issuerUri` on the first login.
A login SHALL open the board only when the id token's subject equals `orchestrator.auth.oidc.allowed`, or its email does and
`email_verified` is `true`; any other is refused 403.
#### Scenario: The allowed subject
- **WHEN** `auth` is `oidc` and the provider signs in the `allowed` subject
- **THEN** the board opens
#### Scenario: Another subject
- **WHEN** `auth` is `oidc` and the provider signs in any other subject
- **THEN** the login is refused 403 and the board stays closed
#### Scenario: The allowed email, verified
- **WHEN** `auth` is `oidc` and the provider signs in a subject whose email is `allowed` with `email_verified` true
- **THEN** the board opens
#### Scenario: The allowed email, unverified
- **WHEN** `auth` is `oidc` and the id token's email is `allowed` and `email_verified` is false or absent
- **THEN** the login is refused 403 and the board stays closed
