# 0246 — An OIDC email counts only when the provider verified it

[← decisions](README.md) · rule: [`oidc-login`](../../openspec/specs/oidc-login/spec.md)

## Status

Accepted 2026-11-25. On [0180](0180-each-spheres-industry-standard-is-jagts-floor.md).

## Decision

- A login matching `orchestrator.auth.oidc.allowed` on the id token's `email` opens the board only when `email_verified` is `true`.
- An absent or false `email_verified` refuses the login 403, as any other identity.
- A subject match is unchanged: the subject is the provider's own identifier, never a claim the user sets.

## Why

- OpenID Connect Core 1.0 §5.1 and OWASP ASVS 5 level 2 treat an unverified email as unauthenticated input;
  a provider letting a user enter any address would let them claim the owner's.
- The floor of [0180](0180-each-spheres-industry-standard-is-jagts-floor.md): authentication trusts only what the provider vouches for.

## Rejected

- A setting to trust unverified email: a second path past the check, for a provider the owner can configure to verify.

## Reopen when

- A provider the owner needs never sends `email_verified` and offers no other stable identifier than its email.
