# 0248 — A host property is read by the host alone

[← decisions](README.md) · rule: [`seams`](../rules/seams.md) · on [0162](0162-a-check-holds-for-all-code.md),
[0130](0130-every-third-party-is-a-configured-seam.md), [0180](0180-each-spheres-industry-standard-is-jagts-floor.md)

## Status

Accepted 2026-11-25.

## Context

- Thirteen main classes read the home, temp or working directory through `System.getProperty`: a test could not move them
  without changing the JVM's own properties.
- `Host` already answers home and working directory; temp was missing.

## Decision

- **Home, temp and working directory come from `Host`** (`home()`, `temp()`, `workingDir()`), implemented in `SystemHost` alone.
- **Row `host-properties-owner`** fails any main class but `SystemHost`, `Executables` and `StandInputs` calling `System.getProperty`.
- **`SystemHost` and `Executables` share `adapter/host`**, exposed to `stand`.
- **The stand's `-D` inputs are read by `StandInputs`**: they are run parameters, not host facts.
- **A class at the collaborator ceiling groups, never grows**: `ConfigReach` takes the sandbox reach out of `ConfigService`,
  `ClaudeEndpoints` joins the two session endpoints, `SessionEnvironment` and `AssistantProcess` carry the host for their callers.
- **A test host is `FakeHost`**, in the use-case test fixtures.

## Rejected

- A `Host.property(name)` reader: it would move the leak behind a string key.

## Reopen when

- A host fact beyond the four properties is read by a second class.
