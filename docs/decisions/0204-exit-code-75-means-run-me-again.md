# 0204 — Exit code 75 means run me again

[← decisions](README.md) · on [0169](0169-stop-work-is-a-smartlifecycle-after-the-web-server.md)

## Status

Accepted 2026-11-19.

## Context

- An upgrade switches `current` to a kept release; the running jar stays the old one until someone restarts it.
- The launchers `install.sh` and `install.ps1` write start the jar by `exec`, so nothing outside the jar can start it again.

## Decision

- **Exit code 75 (`Exit.RUN_AGAIN`, BSD `EX_TEMPFAIL`) means "run me again"**; jagt exits with it after its normal graceful stop.
- The launcher exports `JAGT_LAUNCHER` (`Exit.LAUNCHER_ENV`) and loops: while the jar exits 75 it reads `current` again and starts the jar.
  Any other code ends the launcher with that code.
- `upgrade` exits with 75 only when `JAGT_LAUNCHER` is set; otherwise nobody would start jagt again, so it keeps running and says to restart.
- The exit goes through the `Exit` port; `ProcessExit` runs `SpringApplication.exit`, so the SmartLifecycle stop work runs first.

## Rejected

- Launcher `exec` plus a self-spawned child: the child outlives the stop, and two jagts meet on the port.
- Reading `JAGT_LAUNCHER` as a count or version: presence is the whole fact.

## Reopens when

- A launcher or supervisor needs a second exit meaning; the code table then moves into a spec.
