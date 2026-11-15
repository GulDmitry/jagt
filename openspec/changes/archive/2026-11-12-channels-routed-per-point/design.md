# Design: channels routed per point

## Decisions

| question | answer | why |
|---|---|---|
| pattern, AOP; MilestoneJob; a send with no stored fact | observer on the persisted change: `StateService.onChange` → `notify/PointWatch` → pure catalogue → `ToldOnce`; business code calls no notifier; no weaver, not at the doors or in the reducer; `MilestoneJob` replaced by `PointWatch`; stays a raised point via `Notifications.send(point, title, body)`: install, usage, housekeeping, any send whose fact is not on the task | a point is a fact that landed, the task is the fact, the ledger the ack (0144); 30 writers reach the same facts, per-method advice misses the next writer and fires on refused calls (0067, 0132); `checkerPassed`, `requestLinked` bypass the doors; an unpersisted reduce would tell; `MilestoneJob` was status-only, missed `Checked.Passed`, forgot on restart; each of today's `send` calls mapped in step 6 |
| channels, config | `desktop`, `teams`, `command` (ex-`ChannelNotifier`: script given the point as JSON on stdin); no Slack (owner); no defaults in code, `jagt.yml.dist` writes today's banners out as its last block; no block: starts, one startup note "nothing will be told"; old keys `notifications.desktop`/`.channel` dropped, the owner's `jagt.yml` gets the new block | one source of truth; tests and quiet installs; one user, fresh config |
| points | a new point = one constant in `flow/Point` + one fixture row (`PointDeclarationTest`: no id outside `flow/Point*`); role points in `roles/<id>/role.yml` `points: [passed, round-sent, declined]` → `RoleDefinition.points`, kinds in `flow/RolePointKind`, ids `<role>-<kind>`; Master (`roles/master/role.yml`): sealed, asked-stop, left, unanswered, pressed-deploy (act only), shipped commented; a disabled role contributes none, a route naming one is inert with one startup WARN | `flow/` names no role; Master's verdicts are already on the card; toggling a role needs no route edit |
| told once, restart, time | `ToldOnce.points` by point id on `Round`, never reset, one writer `TaskReducer.told(task, Point, key)`; restart tells standing points silently (0132); `job/PointSweep` every 5 s runs `PointWatch.sweep()` for stands needing time (your-move 5 s) and retries; `PointWatch` an `@EventListener(StateWritten)`, `Checkers` a constructor collaborator, no `@PostConstruct` (0160) | a new round brings new keys; an observer alone has no clock |
| failure, delivery | built in, not a route: one WARN `notification not delivered` (channel, point, task, cause, effect) and one board-head mark via `port/Reach` naming channel, point, cause; stays untold; retry after 10 s, 20 s, then every 30 s (`ChannelPosts` holds the backoff), first success ends it; at least once per (task, point, key), a partial failure re-posts every route | owner: 30 s at most |
| secret, Teams | `webhookEnv: <ENV NAME>`, never a value (0142); Teams = Workflows webhook, Adaptive Card attachment (verified 2026-11-10); Teams MCP connector not used; `{board}` and `your-move` in Teams allowed, not shipped | O365 connectors retired; a model posting in a person's name; loopback URL, a human's move is not team news |

## Catalogue

| point | stands when | key |
|---|---|---|
| your-move | the turn is the human's for 5 s (`Turns`) | turn step + digest |
| stopped | `work.stop` set; the watchdog's silence (`ToldOnce.SILENCE`) | stop key / silence key |
| auto-review | an auto-review window elapsed, a poll gave up, a request is gone | one joined key of the three |
| conflict-stalled | a handed conflict stopped moving and the human was told | `Conflict.told().at()` |
| request-opened; approved | `requests()` non-empty; `round.approved` | sorted request URLs; URLs + tip |
| checks-green / -failed | pipeline result on the tip, no infra retry armed | tip sha |
| deploy-pressed / deployed / deploy-conflict / reverted | deployment set / target landed / `DEPLOY_CONFLICT` / `revertCommit` | pressedAt / merge shas / pressedAt+project / revert sha |
| merged / done | every repo merged / `DONE` | request URLs / done |
| `<role>-passed` / `-round-sent` / `-declined` | `RoleRecord.passed` / `roundDigest` / `declined` | sha / digest / status+digest |
| `master-*`; housekeeping, install, usage | `task/Master` facts; raised by their job | each its own |

## Config

The last block of `jagt.yml.dist` (no route = told to nobody; shown routes are today's banners):
```yaml
notifications:
  channels:
    desktop: { kind: desktop }                          # click opens the task on the board
    # team: { kind: teams, webhookEnv: TEAMS_WEBHOOK }  # env var NAME of the Workflows URL
    # ops:  { kind: command, command: [...] }           # stdin {"event","task","alias","title","body"}
  points:
    your-move:      [ { channel: desktop, title: "jagt · {alias} · {task} · {title}", text: "{body}" } ]
    request-opened: [ { channel: desktop, title: "jagt · {alias} · {task} · request opened", text: "{requests}" } ]
    # deployed: [ { channel: desktop, title: "...", text: "{sha}" }, { channel: team, text: "{alias} is live: {sha} {pipeline}" } ]
```
Placeholders:
`{task} {alias} {ticket} {title} {status} {said} {next} {project} {requests} {request} {pipeline} {sha} {board} {role}
{stop} {origin}`; `{said}` the task's last line, `{next}` its next move (`Move`); `{title} {body}` only on a raised
point; the `.dist` keeps today's wording where a placeholder reproduces it.

## Already in the code

Verified 2026-11-10, reused:
`port/Notification` + `Event` (8 ids; `Event` → `flow/Point`), `port/Notifier` (→ `ChannelDriver`),
`notify/Notifications` (fan-out on `notificationDelivery` executor, WARN on drop; the router),
`adapter/DesktopNotifier` + `port/UserNotifier` (`MacNotifier`, `LibNotifyNotifier`; kind `desktop`),
`adapter/ChannelNotifier` (0142, 10 s; kind `command`), `job/MilestoneJob` (0132, 2 s status diff), `task/ToldOnce`
(0144) + `TaskReducer.told/untold`, `port/Reach`, `ReachLost`, `service/ReachLedger`, `StateWritten`,
`ConfigService` old keys; senders calling `notifications.send` (mapped in step 6): `YourMoveJob`, `WatchdogService`,
`ConflictStall`, `SessionLedger`, `StopAnnouncements`, `AutoReviewScheduler`, `AutoReviewPolls`, `RequestClosedRead`,
`WorktreeOrphanScanner`, `IntakeHistory`, `RunningJarWatch`, `UsageAlerts`, `surface/mcp/tools/StatusTools`.
