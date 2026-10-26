# 0092 — The session sandbox is opt-in

[← decisions](README.md) · rule: [`guards`](../rules/guards.md) · amends [0044](0044-a-session-writes-only-inside-its-task.md)

## Status

Accepted 2026-10-26. Supersedes 0044's "on by default"; the rest of 0044 holds when `sandbox.enabled` is `true`.

## Context

- Spike 2026-10-26, Claude Code sandbox on macOS.
- The sandbox lets the network out only through Claude's local authenticating proxy, set in the `*_PROXY` variables.
- A JVM reads none of them: Gradle fails with `Could not HEAD 'https://repo.maven.apache.org/...' > Operation not permitted`.
- `curl` in the same shell got 200.
- No Claude sandbox key opens direct network (0044's own reopen condition).
- A Java developer's Gradle or Maven must reach the network out of the box.

## Decision

- `orchestrator.agent.sandbox.enabled` defaults to `false`; `true` opts in.
- Off: a session writes wherever the agent CLI's own rules let it, and `ToolGate` and the hooks still judge it.
- On: 0044 as written, and a JVM build cannot reach the network.
- `ClaudeSandboxCheck` runs only when it is on.

## Rejected

- A Gradle init script mapping `HTTPS_PROXY` to `http(s).proxyHost`/`Port`/`User`/`Password`: it resolved,
  but needs a file in `~/.gradle/init.d` on every machine, and Maven needs its own.
- `sandbox.excludedCommands` with `gradle *` and `./gradlew *`: it did not apply to `cd x && gradle ...`
  nor `CI=1 gradle ...`; still `Operation not permitted`.

## Reopen when

- Claude's sandbox opens direct network, or a JVM reads the proxy variables: default on again.
