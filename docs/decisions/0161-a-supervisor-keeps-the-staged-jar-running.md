# 0161 — A supervisor keeps the staged jar running, and a refused start is a page

[← decisions](README.md) · rule: [`seams`](../rules/seams.md) · beside [0130](0130-every-third-party-is-a-configured-seam.md)

## Status

Accepted 2026-11-10 with the commit landing the seam.

## Context

- A rebuilt jar and a refused start needed the machine: `RunningJarWatch` could only say "restart", and a start refused
  by `StartupValidation` left a log line and no board.
- Process supervision is an OS technology (launchd, systemd), so 0130 makes it a seam.

## Decision

- **`Supervisor` is a port** in `core/port`; `supervisor` in `jagt.yml` picks the driver: `none` (default, today's behaviour)
  or `launchd` (`adapter/macos`, `launchctl` by bare name). A driver on the wrong OS is refused at startup (`LaunchdCheck`).
- **The board installs and restarts**: `supervise` and `restart` are `GlobalCommand` beans; neither is the Master's.
  The agent runs the stable `jagt-run.jar` link `stageJar` writes, so a restart picks up the newest stage.
- **A refused start serves `FailurePage`** on the board's port, loopback only, Host and Origin checked: every problem, an
  editor for `jagt.yml`; saving keeps `jagt.yml.bak` and starts again in the same process. A taken port serves nothing.
- **A hand-started jagt ends after `supervise`** through the `Exit` port (`ProcessExit` closes the context, then exits), so the
  agent takes the port; a jagt the agent started (`XPC_SERVICE_NAME` is its label) stays.
- Default `none`, not `launchd`: a seam never starts managing a machine unasked, and Linux has no driver yet.
- Tests use a fake `Processes`; no suite installs a real agent.

## Rejected

- A restart verb without a supervisor: nothing would bring jagt back.

## Reopen when

- A second OS needs a driver, or the failure page must edit more than `jagt.yml`.
