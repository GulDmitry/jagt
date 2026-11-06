# 0142 — A team channel is a configured command

[← decisions](README.md) · rule: [`attention`](../../openspec/specs/attention/spec.md)
· stands under [0130](0130-every-third-party-is-a-configured-seam.md)

## Status

Accepted 2026-11-06.

## Context

- A team wants a request opening and a deploy in its chat; chat services differ and each has its own webhook.
- 0130 makes every third party a seam with a driver per vendor; a webhook URL is a secret.

## Decision

- One vendor-neutral `Notifier`, `channel`, runs `notifications.channel.command` with one JSON object on stdin per listed event.
- The command is the driver: the user's script names the vendor, jagt names none.
- The secret lives in the command's environment, never in `jagt.yml`.
- The wait is bounded; a failure is one WARN and never a task's or the board's.

## Rejected

- A Slack, Teams or Mattermost driver: a vendor in `adapter/` for every service a team may use.
- A webhook URL key in `jagt.yml`: the file would hold a secret.

## Reopen when

- A vendor driver is asked for that a command cannot reach, such as one needing a long-lived connection.
