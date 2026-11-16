## Why
A hosted board behind a password is one more secret to keep; an owner has a single sign-on provider already.

## What Changes
- `orchestrator.auth.mode: oidc` logs the owner in through any OpenID Connect provider and lets one subject or email in.
- A missing issuer, client id, allowed subject or unset secret variable refuses the start.
