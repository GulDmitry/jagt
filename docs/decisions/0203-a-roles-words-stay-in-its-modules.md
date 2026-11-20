# 0203 — A role's words stay in its modules

[← decisions](README.md) · on [0176](0176-every-kind-has-a-ceiling.md)

## Status

Accepted 2026-11-19 (JAGT-133).

## Context

- Roles are optional: core and usecase must run with none installed, so neither names one.

## Decision

- Core and usecase main code names a role only inside that role's modules (`RoleModules`), pinned by `RoleNamesOutsideFitness`.
- What the flow keeps of a role is a pinned kind: the stop kind, the checker kind and the stand-in kind (persisted keys,
  `jagt.yml` keys, file names); the master's neutral word in core is `arbiter`.
- **Ceiling raised by two** ([0176](0176-every-kind-has-a-ceiling.md)): `startupChecks` 33, the acceptance role's
  `AcceptanceConfigCheck` and `AcceptanceRoleCheck` leaving `ConfigCheck` and `RoleCheck`.

## Rejected

- Renaming persisted keys: a stored task or `jagt.yml` written by an older jagt would no longer read.

## Reopens when

- A persisted key gets a migration, and its pin can go.
