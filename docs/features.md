# Features
[← README](../README.md) · Each line names one thing jagt does and links to the requirement that defines it; the spec is the truth, this is its index.

## Acceptance probes · [spec](../openspec/specs/acceptance-probes/spec.md)
- **Declared probes** — A plan lists its acceptance checks as typed probes, never as prose.
  [spec](../openspec/specs/acceptance-probes/spec.md#requirement-a-plan-declares-its-probes-in-one-form)
- **Probes and flows** — jagt records what the plan's probes printed; the acceptance read names flows, run on a listed environment as report rows.
  [spec](../openspec/specs/acceptance-probes/spec.md#requirement-jagt-runs-the-probes-at-verifying)
- **Tagged criteria** — A plan lists the ticket's acceptance criteria, each tagged here, elsewhere or wrong; one without is refused.
  [spec](../openspec/specs/acceptance-probes/spec.md#requirement-a-plan-carries-its-tagged-acceptance-criteria)

## Attention · [spec](../openspec/specs/attention/spec.md)
- **Questions come first** — A session that needs an answer says so before anything else, and the move becomes yours.
  [spec](../openspec/specs/attention/spec.md#requirement-a-question-is-reported-first)
- **Silence is noticed** — A stalled, crashed or waiting session is flagged without you looking, from one session record a restart keeps.
  [spec](../openspec/specs/attention/spec.md#requirement-silence-is-read-by-the-watchdog-and-recorded-by-the-reducer)
- **Attention tiers** — What needs you is ranked, counted in the header and filterable by whose move it is.
  [spec](../openspec/specs/attention/spec.md#requirement-attention-tiers)
- **Badge names the act** — Each badge says which action waits for you, with a sentence in its tooltip.
  [spec](../openspec/specs/attention/spec.md#requirement-the-badge-names-the-act)
- **Notifications routed per point** — `notifications.channels` and `notifications.points` route each point of a task's life (`your-move`, `stopped`,
  `auto-review`, `conflict-stalled`, `deployed`, …) to a channel, once, with your own text.
  [spec](../openspec/specs/notification-routing/spec.md#requirement-a-point-is-routed-to-the-channels-jagtyml-lists-told-once-per-key)
- **Teams channel** — A `teams` channel posts a point as an Adaptive Card to a Workflows webhook, its links as buttons.
  [spec](../openspec/specs/notification-routing/spec.md#requirement-a-teams-channel-posts-an-adaptive-card-to-a-workflows-webhook)

## Board · [spec](../openspec/specs/board/spec.md) · [oidc login](../openspec/specs/oidc-login/spec.md)
- **One surface** — Cards, buttons and gates read the same rules. [spec](../openspec/specs/board/spec.md#requirement-one-surface-one-projection)
- **Judge on the board** — A failed check's output, what Trust adopts, `tail` and `worktrees`. [spec](../openspec/specs/board-judgement/spec.md)
- **Command palette** — Type a command, or free text that a model turns into one proposed command.
  [spec](../openspec/specs/board-verbs/spec.md#requirement-tier-1-is-grammar-tier-2-only-proposes)
- **Lost reach on the head** — A lost reach (code host, MCP server) is one mark on the head, never a card's stop; its login runs on the Session tab.
  [spec](../openspec/specs/board/spec.md#requirement-a-lost-reach-shows-in-the-boards-head)
- **Theme and language on the gear** — The gear chooses both per browser; a catalog file adds a language. [spec](../openspec/specs/board/spec.md)
- **Board as its own app** — `board/` from the jar or `orchestrator.board.dir`; coupled by `contracts/`. [spec](../openspec/specs/board-app/spec.md)
- **Board access** — Loopback, `password` or `oidc`; refuses foreign writes; own-origin CSP, login back-off, audit line, owner-only metrics.
  [spec](../openspec/specs/board/spec.md)
  [spec](../openspec/specs/board-security/spec.md)

## Board workspace · [spec](../openspec/specs/board-workspace/spec.md)
- **One row per task, the open one beside the list** — A row shows status, age and checks; pick one and its detail opens beside the list.
  [row](../openspec/specs/board-workspace/spec.md#requirement-a-task-is-one-row) ·
  [open](../openspec/specs/board-workspace/spec.md#requirement-one-task-is-open-beside-the-list)
- **The open task's tabs** — Session, Artifacts, Code and Diff, and the shell line the Session tab runs: [their own page](features/board.md).
- **Add a project, change a setting, run a job** — Projects and the basic `jagt.yml` settings from the gear; any job runs now.
  [settings](../openspec/specs/project-setup/spec.md) · [run](../openspec/specs/board-workspace/spec.md#requirement-every-job-can-run-now)
- **Project by url** — A `url` with no existing `path` is cloned under the data folder; a failed clone marks the head. `localFiles` feeds `copyGlobs`.
  [spec](../openspec/specs/project-setup/spec.md#requirement-a-project-named-by-url-is-cloned)
- **Project hover in the new-task form** — Hovering a project shows its `jagt.yml` entry and remote; Start with no project needs `plan first`.
  [spec](../openspec/specs/board/spec.md#requirement-a-mark-says-what-it-replaces)
- **Keys for verbs, who asked** — Every verb shows its key, even one that asks first; each history step names who asked.
  [keys](../openspec/specs/board-workspace/spec.md#requirement-a-verb-carries-its-key) ·
  [who](../openspec/specs/board-workspace/spec.md#requirement-the-history-names-who-asked)

## Cases · [spec](../openspec/specs/cases/spec.md) · [debug reads](../openspec/specs/debug-reads/spec.md)
- **Your own cases** — A case sends a step and opens a turn, or lets the Master move.
  [spec](../openspec/specs/cases/spec.md#requirement-the-cases-are-files-you-may-copy)
- **Debug reads** — A local CLI reads the log, stops, a task's transcript and the Master's feed through `/mcp` or `/api/debug`, changing nothing.
  [spec](../openspec/specs/debug-reads/spec.md#requirement-a-running-jagt-is-read-never-changed-through-its-own-door)

## Fitness functions · [kinds](../ARCHITECTURE.md#the-kinds)
- **Fitness functions in one file** — `fitness.yml` declares each in a reserved tree and each module's exposed packages; `./gradlew fitness` runs all.
  [spec](../openspec/specs/fitness/spec.md#requirement-a-fitness-function-reports-its-number-against-its-threshold)

## Git · [spec](../openspec/specs/git/spec.md)
- **Deploy and revert** — Only these two write a shared branch; a revert picks from listed merges, `hand-off` gives the session a git case.
  [spec](../openspec/specs/board-verbs/spec.md#requirement-a-git-case-the-session-can-work-has-a-verb)
  [spec](../openspec/specs/revert/spec.md#requirement-revert-takes-out-the-last-deploy-or-refuses)
- **Ship** — Ship has the session commit, push and open or update its review request against the base it reported; with no code host, push alone.
  [spec](../openspec/specs/git/spec.md#requirement-ship-hands-the-work-to-the-agent)
- **A repository kept on this machine** — `remote: none` cuts worktrees from the local base, pushes nothing and deploys into the local branch.
  [spec](../openspec/specs/task-start/spec.md#requirement-a-project-names-the-remote-its-branches-are-shared-through)
- **Several repositories** — One task spans several repositories, a worktree in each; `add-repo` attaches one, `remove-repo` drops one.
  [spec](../openspec/specs/repo-attach/spec.md#requirement-a-running-task-gains-a-repository)
- **Trust before credentials** — Git runs with your credentials only under config and hooks you trusted; only you adopt a change.
  [spec](../openspec/specs/git-trust/spec.md#requirement-git-with-the-humans-credentials-waits-for-trust)
  [spec](../openspec/specs/git-trust/spec.md#requirement-only-the-human-adopts-a-change)
- **Guards** — A session writes only in its worktrees and tool caches, pushes only its own branch, no force or skipped hooks.
  [spec](../openspec/specs/guards/spec.md#requirement-a-session-writes-only-inside-its-task)
  [spec](../openspec/specs/guards/spec.md#requirement-a-refusal-is-counted-on-its-task)
  [spec](../openspec/specs/git/spec.md#requirement-push-guard-lives-in-the-worktree)

## Judgement resilience · [spec](../openspec/specs/judgement-resilience/spec.md)
- **Majority of runs** — An eval case passes when most of its runs do; `-PevalRuns` and `-PevalPasses` set the quorum.
  [spec](../openspec/specs/judgement-resilience/spec.md#requirement-an-eval-case-passes-on-a-majority-of-runs)
- **What jagt heard** — `roleEval` asserts the read gate heard a call in the round's worktree; a Master pattern is one yes-or-no fact.
  [spec](../openspec/specs/judgement-resilience/spec.md#requirement-an-eval-asserts-what-jagt-heard-not-only-what-the-model-said)
  [spec](../openspec/specs/judgement-resilience/spec.md#requirement-a-pattern-the-master-stops-on-is-one-yes-or-no-fact)

## Master · [spec](../openspec/specs/master/spec.md)
- **Master review** — A headless reader judges every handed-back round and writes its verdict for you.
  [spec](../openspec/specs/master/spec.md#requirement-the-master-is-a-headless-run-per-role-not-a-window)
- **Master brief** — The Master judges by a brief you copy and edit. [spec](../openspec/specs/master/spec.md#requirement-the-master-judges-by-a-brief)
- **Master acts for you** — The Master ships and answers; the plan and the deploy stay yours. Key: `orchestrator.roles.master.mode`, `act` by default.
  [spec](../openspec/specs/master/spec.md#requirement-in-act-the-master-moves-for-you)
- **Lessons** — When you disagree with the Master, it offers a lesson and changes no brief.
  [spec](../openspec/specs/master/spec.md#requirement-a-disagreement-offers-a-lesson-never-applies-one)

## Platform · [spec](../openspec/specs/platform/spec.md) · [supervisor](../openspec/specs/supervisor/spec.md)
- **Keep awake** — The machine stays awake while the Master acts, polls run or tasks are being taken. Key: `orchestrator.keepAwake`, off by default.
  [spec](../openspec/specs/platform/spec.md#requirement-the-machine-stays-awake-only-when-asked)
- **English and UTC** — Every time on the board and in a report is UTC and every text English, whatever the machine's or browser's locale.
  [spec](../openspec/specs/platform/spec.md#requirement-jagt-shows-every-time-in-utc-and-every-text-in-english)

## Protocol · [spec](../openspec/specs/protocol/spec.md)
- **Reports validated once** — A session's report is checked at the door and every violation comes back together, typed.
  [spec](../openspec/specs/protocol/spec.md#requirement-every-violation-at-once)
- **Tickets through your MCP** — Tracker and code host are read through your own MCP servers; jagt holds no token.
  [spec](../openspec/specs/protocol/spec.md#requirement-a-ticket-is-read-through-the-humans-mcp-servers)
- **Tracker intake and close** — Ready tickets open tasks, done ones close them, Markdown files too. Key: `orchestrator.tracker.mode`, off by default.
  [spec](../openspec/specs/protocol/spec.md#requirement-tracker-stages-open-and-close-tasks)
- **Says why** — A session's report and a new task each give one sentence why, read in the step's hover.
  [spec](../openspec/specs/protocol/spec.md#requirement-a-request-says-why)

## Review · [spec](../openspec/specs/review/spec.md)
- **Review threads** — Each is fixed, declined with a why, or asked. [spec](../openspec/specs/review/spec.md#requirement-the-brief-routes-each-thread)
- **Drafted replies** — Replies to reviewers are drafted into a file you read before anything is posted.
  [spec](../openspec/specs/review/spec.md#requirement-drafted-replies-are-a-file)
- **Take over** — A new task can take over an open review request. [spec](../openspec/specs/review/spec.md#requirement-resume-takes-over-a-request)
- **Infrastructure waits** — A red pipeline goes back to its session unless the host blames infrastructure; a `wait` probes on, then stops.
  [spec](../openspec/specs/review/spec.md#requirement-a-red-round-goes-back-to-the-session)
  [spec](../openspec/specs/infra-wait/spec.md#requirement-infrastructure-failures-are-waited-out)
  [spec](../openspec/specs/review/spec.md#requirement-pipeline-status-is-one-of-jagts-five-words)
  [spec](../openspec/specs/infra-wait/spec.md#requirement-a-session-waits-out-a-dependency-that-is-down)

## Runtime · [spec](../openspec/specs/runtime/spec.md)
- **Sessions in tmux** — Each agent runs in a tmux window, viewed in kitty or Ghostty (`orchestrator.terminal`),
  on `orchestrator.agent.model` where the notes name none. [spec](../openspec/specs/runtime/spec.md#requirement-the-backend-owns-no-terminal)
- **Viewer detaches** — A closed viewer never stops a session. [spec](../openspec/specs/runtime/spec.md#requirement-closing-the-viewer-only-detaches)
- **Revive after a restart** — Sessions return where they were; one that wrote while jagt was down is asked once to repeat its report.
  [spec](../openspec/specs/runtime/spec.md#requirement-what-jagt-revives-it-re-enters)
  [spec](../openspec/specs/session-brief/spec.md#requirement-a-restart-asks-for-the-report-it-may-have-lost)
- **Stop ends sessions as gone** — with `onStop: end`, SIGTERM ends every live session as gone, windows closed; the next start goes on from the notes.
  [spec](../openspec/specs/shutdown/spec.md)
- **Cold prompts** — Past the cache lifetime, a typed prompt starts fresh. [spec](../openspec/specs/cold-prompts/spec.md)
- **Open in your editor** — Open a worktree or diff in your editor. [spec](../openspec/specs/platform/spec.md#requirement-the-editor-is-a-launcher)
- **Pinned servers** — A session loads jagt's MCP server and the ones you pin, none of yours. Key: `orchestrator.claude.mcpConfig`.
  [spec](../openspec/specs/runtime/spec.md#requirement-a-sessions-servers-can-be-pinned)

## Session brief · [spec](../openspec/specs/session-brief/spec.md)
- **Your own brief** — What jagt tells a session at each step is a section of `roles/session/brief.md.dist`; copy it to edit one.
  [spec](../openspec/specs/session-brief/spec.md#requirement-the-brief-is-a-file-you-may-copy)
- **Autoplan** — A session plans first, unasked, when its task is large or unclear. Key: `orchestrator.agent.autoplan.enabled`, off by default.
  [spec](../openspec/specs/session-brief/spec.md#requirement-autoplan)
- **Visualize** — A session sketches a task worth a picture before its first edit. Key: `orchestrator.agent.visualize.enabled`, on in the dist.
  [spec](../openspec/specs/session-brief/spec.md#requirement-visualize)
- **Diagnose** — A session diagnoses a bug from facts and a control before code. Key: `orchestrator.agent.diagnose.enabled`, off by default.
  [spec](../openspec/specs/session-brief/spec.md#requirement-diagnose)
- **Switch judgements** — A session says yes or no, with why, to each switch that is on, and the board shows it.
  [spec](../openspec/specs/session-brief/spec.md#requirement-a-session-judges-each-switch-that-is-on)

## Spec changes · [spec](../openspec/specs/spec-changes/spec.md)
- **Spec change and fold** — A hand-back is refused until the task's spec change validates; a ship folds it into the specs in its commit.
  [hand-back](../openspec/specs/spec-changes/spec.md#requirement-a-hand-back-carries-a-valid-change) ·
  [ship](../openspec/specs/spec-changes/spec.md#requirement-a-ship-folds-the-change-into-the-specs)
- **Engineering gates** — Each gate: [spec](../openspec/specs/engineering/spec.md), [practices](practices.md), [stand](features/stand-engines.md).

## Startup · [spec](../openspec/specs/startup/spec.md) · [agent seams](../openspec/specs/agent-seams/spec.md)
- **Install checks** — A missing part of the installation is refused at start, with no network. [spec](../openspec/specs/startup/spec.md)
- **One-line install** — `install.sh` and `install.ps1` install a verified jar and Java runtime per user; it starts with no checkout or project.
  [spec](../openspec/specs/release-artifacts/spec.md#requirement-one-line-installs-jagt-per-user)
- **Upgrade** — `upgrade` switches to a newer kept release and restarts on it under the launcher. [spec](../openspec/specs/board-verbs/spec.md)
- **One configuration file** — Options live in `jagt.yml`, a role's own in `roles/<id>/role.yml`; one that cannot check is a board mark.
  [spec](../openspec/specs/startup/spec.md#requirement-a-role-folder-that-cannot-run-a-check-is-the-boards)
- **Server image** — Yours adds tools, agent seed. [spec](../openspec/specs/readiness/spec.md) · [env](../openspec/specs/server-environment/spec.md)
- **Signed release** — SBOM, SHA-256 and signed provenance for the jar and the image. [spec](../openspec/specs/release-artifacts/spec.md)
- **One data folder** — State, records, tasks, clones and logs live under `<root>/data`, or the folder `orchestrator.data` names.
  [spec](../openspec/specs/startup/spec.md#requirement-one-data-folder-holds-everything-jagt-writes)
- **Console logging** — `logging: console` writes every log line as one ECS JSON object on stdout and no file; `file` is the default.
  [spec](../openspec/specs/startup/spec.md#requirement-logging-is-a-seam-file-or-console)
- **Values from the environment** — `${VAR}`, `${VAR:default}` and `${file:/path}` fill a value; `JAGT__a__b=value` sets the key `a.b`.
  [spec](../openspec/specs/startup/spec.md#requirement-a-value-may-come-from-the-environment-or-a-file)
- **Local reads** — A local model may map commands, else the cloud; a down one is a lost reach. Key: `orchestrator.assistant.local.endpoint`, off.
  [spec](../openspec/specs/agent-seams/spec.md#requirement-a-read-that-needs-no-tool-may-ask-a-local-model)
- **Codex and OpenCode agents** — `agent.cli: codex` or `opencode` runs sessions there, `opencode.reads`, off, a role's reads.
  [spec](../openspec/specs/agent-seams/spec.md#requirement-a-read-named-under-reads-runs-on-opencode)

## Task flow · [spec](../openspec/specs/task-flow/spec.md)
- **The life of a task** — statuses, verify, deploy check, acceptance workspace, start and plan first: [their own page](features/task-flow.md).

## Unattended work · [spec](../openspec/specs/unattended-work/spec.md)
- **Auto-review** — jagt polls an open request for comments, only reading and drafting. Key: `orchestrator.autoReview.enabled`, off by default.
  [spec](../openspec/specs/unattended-work/spec.md#requirement-the-auto-review-poll-only-reads-and-drafts)
- **Task activity log** — `activity` lists every status change of live and finished tasks, filtered by kind and project; a live task's line opens it.
  [spec](../openspec/specs/unattended-work/spec.md#requirement-the-activity-report-is-the-task-activity-log)
- **Stops and what ends them** — Bad endings, the hourly cap, loops, pauses and `stop` halt work until lifted; a failure lifts on a stronger tier.
  [spec](../openspec/specs/unattended-work/spec.md#requirement-act-stops-itself-after-three-bad-endings)
  [spec](../openspec/specs/unattended-work/spec.md#requirement-a-task-has-a-ceiling-of-unattended-moves-an-hour)
  [spec](../openspec/specs/task-stops/spec.md#requirement-jagt-stops-a-task-it-suspects-it-is-looping)
  [spec](../openspec/specs/cases/spec.md#requirement-what-no-case-names-is-a-miss)
  [spec](../openspec/specs/task-stops/spec.md#requirement-in-act-the-master-decides-a-stop-for-a-suspected-loop)
  [spec](../openspec/specs/task-stops/spec.md#requirement-triage-reads-a-stop-onto-its-hover)
  [spec](../openspec/specs/task-stops/spec.md#requirement-you-pause-a-started-task)
  [spec](../openspec/specs/board-verbs/spec.md#requirement-a-board-wide-stop-halts-all-work-until-your-lift)
  [spec](../openspec/specs/task-stops/spec.md#requirement-a-stop-lifts-on-the-next-tier)

## Usage limits · [spec](../openspec/specs/usage-limits/spec.md)
- **Limits and spend** — warned once per period, a spend ceiling, the week's cost per accepted task: [their own page](features/usage-limits.md).
