# 0033 — jagt reads the CLI's own login for usage, and keeps none

[← decisions](README.md) · rule: [`seams.md`](../rules/seams.md)

## Status

Accepted 2026-10-11. Amends [0020](0020-jagt-holds-no-token-of-its-own.md): jagt still holds no tracker or
code-host credential.

## Context

- The agent CLIs run on a subscription: a 5-hour window, a weekly one, weekly ones per model, and paid extra
  usage up to a monthly cap once a window runs out.
- Neither CLI tells jagt how full a window is, so a session crossing into paid usage spends money unseen.
- Each vendor answers it at an OAuth usage endpoint, authenticated by the login its CLI already stores: the
  macOS Keychain or a credentials file.
- Those endpoints are undocumented: their shape can change without notice.

## Decision

- jagt reads the stored login at each call and keeps it nowhere: not in a field, `state.json`, a log line, an
  exception message or the board.
- It never refreshes the login: an expired one is a read that failed until the CLI runs again.
- One `SubscriptionUsage` adapter per CLI, selected by `orchestrator.agent.cli`; a shape it does not know is
  "could not read", never zero, and a login without a subscription is its own answer.
- jagt shows and tells; nothing it reads refuses or slows work.

## Rejected

- A headless model call reading usage: tokens spent to count tokens, and no answer the model can see.
- Refreshing the token: jagt would write the CLI's login and race it for the one refresh token.
- Gating or throttling near a limit: paying to keep working is the human's call.
- Keeping the token between polls: a credential in memory behind a board that asks for no password.

## Reopen when

- A CLI reports its usage in a file or command jagt can read without a credential.
- A vendor documents the endpoint, or retires it.
- A token is ever seen in a log line, `state.json` or the board.
