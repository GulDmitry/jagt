# 0219 — The board's doors are Spring Security filter chains

[← decisions](README.md) · rule: [`guards`](../rules/guards.md) · amends [0167](0167-the-board-is-guarded-by-an-auth-seam-on-spring-security.md) ·
stands under [0087](0087-the-industry-standard-library-before-our-own-code.md)

## Status

Accepted 2026-11-21 with the commit replacing the four filters.

## Context

- Four hand-written `OncePerRequestFilter`s judged the doors beside Spring Security; two shared one `@Order`.
- `/mcp` was matched two ways, and each filter normalised `;params` and `%xx` by hand: four Defect-family H fixes since 2026-11-08.

## Decision

- **One `SecurityFilterChain` per door, first match wins** (`Doors`): requests the page may not make (`NotThePage`, 403),
  `/mcp` and `/api/agent/` (`LoopbackOrRootToken`, 401), then the board's own chain by `orchestrator.auth` mode.
- **The door paths are one `RequestMatcher`** (`AgentDoor`); `StrictHttpFirewall` is a bean and rejects a respelled path with 400.
- **Refusal is its own chain** rather than an authorisation rule: the login filters run before authorisation, so a rule
  would let a foreign Origin reach the password check.
- **The login lockout stays jagt's own** (0217), as an `AuthenticationProvider` ahead of the password check: Spring Security
  has no per-peer lockout, and an account-status failure ends the provider chain without counting as a new failure.

## Rejected

- One chain with `access(allOf(...))` rules: `formLogin().permitAll()` and the authentication filters bypass them.
- A `Filter` bean for the lockout: its order against the chain is again a hand-kept number.

## Reopen when

- Spring Security ships a per-peer login throttle.
