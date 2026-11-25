# oidc-login Specification

## Purpose
Who may log in to the board through an OpenID Connect provider, and what refuses the start.

## Requirements

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

### Requirement: An incomplete oidc setting refuses the start
`orchestrator.auth.oidc` SHALL hold `issuerUri`, `clientId`, `allowed` and `clientSecretEnv`, the NAME of the environment variable
holding the client secret, never the secret. A missing key, or a `clientSecretEnv` naming an unset variable, SHALL refuse the start naming that key.
#### Scenario: A secret variable not set
- **WHEN** `auth` is `oidc` and `clientSecretEnv` names a variable that is not set
- **THEN** the start is refused naming `orchestrator.auth.oidc.clientSecretEnv`

### Requirement: The provider returns to the public URL
The redirect URI sent to the provider SHALL be the board's public address, `server.publicUrl`, followed by `/login/oauth2/code/oidc`.
#### Scenario: Login behind an ingress
- **WHEN** `server.publicUrl` is `https://jagt.example.org` and a login starts
- **THEN** the provider is asked to return to `https://jagt.example.org/login/oauth2/code/oidc`
