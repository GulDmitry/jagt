# 0167 — The board is guarded by an auth seam on Spring Security

[← decisions](README.md) · rule: [`seams`](../rules/seams.md) ·
stands under [0130](0130-every-third-party-is-a-configured-seam.md) · beside [0025](0025-the-board-is-no-way-around-the-token.md)

## Status

Accepted 2026-11-15 with the commit landing `orchestrator.auth`.

## Context

- The board deploys, closes a task and starts an agent; the only guard was loopback, so a board behind a private
  network or an ingress had none (`guards.md`: a non-loopback `server.address` passes).
- Sessions and the Master reach `/mcp` and `/api/agent/` by their own token and header; a login on those would stop them.

## Decision

- **`orchestrator.auth` is a seam in `jagt.yml`**: `none` (default) or `password`; further modes are further chains.
- **Spring Security is the library** (AGENTS.md "Code": the standard one before our own): one chain per mode,
  chosen by `@ConditionalOnProperty`; the BCrypt encoder and form login are its own.
- **`none` is loopback only**: a `server.address` beyond it, or `auth.allowedHosts`, refuses the start naming `auth`
  (`AuthCheck`, which runs with the checks off too).
- **`password` is one bcrypt hash in `jagt.yml`**, never the password and never logged (`AuthProperties.toString`).
  It guards the board, `/api` and its streams: a browser is sent to `login.html`, `/api` answers 401.
- **`/mcp` and `/api/agent/` stay outside the login**, as `BoardWriteFilter` already treats them; the catalogs,
  `login.html`, `app.css` and `core/i18n.js` are open because the login page needs its words.
- **`LoopbackFilter` stays first** and lets a Host or Origin through only when its name is in `auth.allowedHosts`.
- **CSRF stays off in Spring Security**: `LoopbackFilter` judges every Origin and `BoardWriteFilter` demands one on a write.

## Rejected

- A reverse proxy's basic auth alone: jagt would still bind off loopback with nothing of its own behind it.
- Our own session and cookie code: the library is the standard and carries the fixation and encoder rules.

## Reopen when

- A second mode (SSO) lands: its chain joins this one, the guards row stays.
- A request reaches `/mcp` or `/api/agent/` from an ingress's network and acts: those two then need a guard of their own.
