# Features

[← README](../README.md) · Each line names one thing jagt does and links to the requirement that defines it; the spec is the truth, this is its index.

## Acceptance probes · [spec](../openspec/specs/acceptance-probes/spec.md)

- **Declared probes** — A plan lists its acceptance checks as typed probes, never as prose.
  [spec](../openspec/specs/acceptance-probes/spec.md#requirement-a-plan-declares-its-probes-in-one-form)
- **Probes run by jagt** — jagt runs the plan's probes itself before a hand-back and records what they printed.
  [spec](../openspec/specs/acceptance-probes/spec.md#requirement-jagt-runs-the-probes-at-verifying)
- **Tagged criteria** — A plan lists the ticket's acceptance criteria, each tagged here, elsewhere or wrong; one without is refused.
  [spec](../openspec/specs/acceptance-probes/spec.md#requirement-a-plan-carries-its-tagged-acceptance-criteria)

## Attention · [spec](../openspec/specs/attention/spec.md)

- **Questions come first** — A session that needs an answer says so before anything else, and the move becomes yours.
  [spec](../openspec/specs/attention/spec.md#requirement-a-question-is-reported-first)
- **Silence is noticed** — A stalled, crashed or waiting session is flagged on the board without you looking.
  [spec](../openspec/specs/attention/spec.md#requirement-silence-is-stamped-by-the-watchdog)
- **Attention tiers** — What needs you is ranked, counted in the header and filterable by whose move it is.
  [spec](../openspec/specs/attention/spec.md#requirement-attention-tiers)
- **Badge names the act** — Each badge says which action waits for you, with a sentence in its tooltip.
  [spec](../openspec/specs/attention/spec.md#requirement-the-badge-names-the-act)

## Board · [spec](../openspec/specs/board/spec.md)

- **One surface** — The board is the only surface; every card, button and gate reads the same rules.
  [spec](../openspec/specs/board/spec.md#requirement-one-surface-one-projection)
- **Command palette** — Type a command, or free text that a model turns into one proposed command.
  [spec](../openspec/specs/board/spec.md#requirement-tier-1-is-grammar-tier-2-only-proposes)
- **Local only** — The board answers locally only and refuses foreign writes. [spec](../openspec/specs/board/spec.md#requirement-loopback-only)

## Board workspace · [spec](../openspec/specs/board-workspace/spec.md)

- **One row per task** — Each task is a row with its status, age and checks, kept in its place.
  [spec](../openspec/specs/board-workspace/spec.md#requirement-a-task-is-one-row)
- **Open task beside the list** — Pick a task and its detail opens next to the list.
  [spec](../openspec/specs/board-workspace/spec.md#requirement-one-task-is-open-beside-the-list)
- **Session and Files tabs** — Read the session's screen as it stands and type a line to it; read a task's plan, notes and sketch rendered.
  [spec](../openspec/specs/board-workspace/spec.md#requirement-the-open-task-has-four-tabs)
- **Add a project** — Add a repository to the configuration from the board, never by hand.
  [spec](../openspec/specs/board-workspace/spec.md#requirement-a-project-is-added-from-the-board)
- **Run a job now** — Any unattended job runs now, from its row. [spec](../openspec/specs/board-workspace/spec.md#requirement-every-job-can-run-now)
- **Keys for verbs** — Every verb shows its key, and one that asks first asks on its key too.
  [spec](../openspec/specs/board-workspace/spec.md#requirement-a-verb-carries-its-key)
- **Who asked** — Each step of a task's history names who asked for it, the first one what opened the task.
  [spec](../openspec/specs/board-workspace/spec.md#requirement-the-history-names-who-asked)

## Cases · [spec](../openspec/specs/cases/spec.md)

- **Your own cases** — A case sends a step and opens a turn, or lets the Master move.
  [spec](../openspec/specs/cases/spec.md#requirement-the-cases-are-files-you-may-copy)

## Git · [spec](../openspec/specs/git/spec.md)

- **Deploy and revert** — Only these two verbs write a shared branch; the base branch is never touched.
  [spec](../openspec/specs/git/spec.md#requirement-only-deploy-and-revert-write-shared-branches)
- **Revert** — A revert walks back what a deploy merged, or refuses with a recipe to finish by hand.
  [spec](../openspec/specs/git/spec.md#requirement-revert-refuses-rather-than-guess)
- **Ship** — Ship has the session commit, push and open or update its review request against the base it reported.
  [spec](../openspec/specs/git/spec.md#requirement-ship-hands-the-work-to-the-agent)
- **Push guard** — A session cannot push off its task's branch, force, delete or skip hooks.
  [spec](../openspec/specs/git/spec.md#requirement-push-guard-lives-in-the-worktree)
- **Several repositories** — One task spans several repositories, a worktree in each; `add-repo` attaches one a session asks for.
  [spec](../openspec/specs/git/spec.md#requirement-a-running-task-gains-a-repository)

## Git trust · [spec](../openspec/specs/git-trust/spec.md)

- **Trust before credentials** — Git runs with your credentials only under config and hooks you have trusted.
  [spec](../openspec/specs/git-trust/spec.md#requirement-git-with-the-humans-credentials-waits-for-trust)
- **Adopting a change** — Only you adopt a changed config or hook, in every Master mode.
  [spec](../openspec/specs/git-trust/spec.md#requirement-only-the-human-adopts-a-change)

## Guards · [spec](../openspec/specs/guards/spec.md)

- **Refusals are counted** — Each guard refusal is counted on its task, so a gap reopens on a number.
  [spec](../openspec/specs/guards/spec.md#requirement-a-refusal-is-counted-on-its-task)
- **Writes stay inside the task** — A session's shell writes only inside its task's worktrees and the tool caches.
  [spec](../openspec/specs/guards/spec.md#requirement-a-session-writes-only-inside-its-task)

## Judgement resilience · [spec](../openspec/specs/judgement-resilience/spec.md)

- **Majority of runs** — An eval case passes when most of its runs do; `-PevalRuns` and `-PevalPasses` set the quorum.
  [spec](../openspec/specs/judgement-resilience/spec.md#requirement-an-eval-case-passes-on-a-majority-of-runs)
- **What jagt heard** — `masterEval` also asserts the read gate heard a call made in the round's worktree.
  [spec](../openspec/specs/judgement-resilience/spec.md#requirement-an-eval-asserts-what-jagt-heard-not-only-what-the-model-said)
- **Yes-or-no patterns** — A pattern the Master stops on is one fact answered yes or no on the diff.
  [spec](../openspec/specs/judgement-resilience/spec.md#requirement-a-pattern-the-master-stops-on-is-one-yes-or-no-fact)

## Master · [spec](../openspec/specs/master/spec.md)

- **Master review** — A headless reader judges every handed-back round and writes its verdict for you.
  [spec](../openspec/specs/master/spec.md#requirement-the-master-is-a-headless-run-per-role-not-a-window)
- **Master brief** — The Master judges by a brief you copy and edit. [spec](../openspec/specs/master/spec.md#requirement-the-master-judges-by-a-brief)
- **Master acts for you** — In `act` the Master ships and deploys; your word overrules it. Key: `orchestrator.master.mode`, `judge` by default.
  [spec](../openspec/specs/master/spec.md#requirement-in-act-the-master-moves-for-you)
- **Lessons** — When you disagree with the Master, it offers a lesson and changes no brief.
  [spec](../openspec/specs/master/spec.md#requirement-a-disagreement-offers-a-lesson-never-applies-one)

## Platform · [spec](../openspec/specs/platform/spec.md)

- **Keep awake** — The machine stays awake while the Master acts, polls run or tasks are being taken. Key: `orchestrator.keepAwake`, off by default.
  [spec](../openspec/specs/platform/spec.md#requirement-the-machine-stays-awake-only-when-asked)
- **English and UTC** — Every time on the board and in a report is UTC and every text English, whatever the machine's or browser's locale.
  [spec](../openspec/specs/platform/spec.md#requirement-jagt-shows-every-time-in-utc-and-every-text-in-english)

## Protocol · [spec](../openspec/specs/protocol/spec.md)

- **Reports validated once** — A session's report is checked at the door and every violation comes back together, typed.
  [spec](../openspec/specs/protocol/spec.md#requirement-every-violation-at-once)
- **Tickets through your MCP** — Tracker and code host are read through your own MCP servers; jagt holds no token.
  [spec](../openspec/specs/protocol/spec.md#requirement-a-ticket-is-read-through-the-humans-mcp-servers)
- **Tracker intake and close** — A ready ticket opens a task, and a done ticket closes it. Key: `orchestrator.tracker.mode`, off by default.
  [spec](../openspec/specs/protocol/spec.md#requirement-tracker-stages-open-and-close-tasks)
- **Says why** — A session's report and a new task each give one sentence why, read in the step's hover.
  [spec](../openspec/specs/protocol/spec.md#requirement-a-request-says-why)

## Review · [spec](../openspec/specs/review/spec.md)

- **Review threads** — Each is fixed, declined with a why, or asked. [spec](../openspec/specs/review/spec.md#requirement-the-brief-routes-each-thread)
- **Drafted replies** — Replies to reviewers are drafted into a file you read before anything is posted.
  [spec](../openspec/specs/review/spec.md#requirement-drafted-replies-are-a-file)
- **Take over** — A new task can take over an open review request. [spec](../openspec/specs/review/spec.md#requirement-resume-takes-over-a-request)
- **Red round** — A failing pipeline sends the task back to its session.
  [spec](../openspec/specs/review/spec.md#requirement-a-red-round-goes-back-to-the-session)
- **Unreadable checks** — Three reads running that cannot list the pipelines stop the task, with the reads' causes.
  [spec](../openspec/specs/review/spec.md#requirement-pipeline-status-keeps-the-hosts-wording)

## Runtime · [spec](../openspec/specs/runtime/spec.md)

- **Sessions in tmux** — Each agent runs in a tmux window, and a kitty window attaches to it, on `orchestrator.agent.model` where the notes name none.
  [spec](../openspec/specs/runtime/spec.md#requirement-the-backend-owns-no-terminal)
- **Viewer detaches** — A closed viewer never stops a session. [spec](../openspec/specs/runtime/spec.md#requirement-closing-the-viewer-only-detaches)
- **Revive after a restart** — Sessions return where they were, each holding the turn asked once to repeat its last report.
  [spec](../openspec/specs/runtime/spec.md#requirement-what-jagt-revives-it-re-enters)
  [spec](../openspec/specs/session-brief/spec.md#requirement-a-restart-asks-for-the-report-it-may-have-lost)
- **Open in your editor** — Open a worktree or diff in your editor. [spec](../openspec/specs/runtime/spec.md#requirement-the-editor-is-a-launcher)
- **Pinned servers** — A session loads jagt's MCP server and the ones you pin, none of yours. Key: `orchestrator.claude.mcpConfig`.
  [spec](../openspec/specs/runtime/spec.md#requirement-a-sessions-servers-can-be-pinned)

## Session brief · [spec](../openspec/specs/session-brief/spec.md)

- **Your own brief** — What jagt tells a session at each step is a section of `session-brief.md.dist`; copy it to edit one.
  [spec](../openspec/specs/session-brief/spec.md#requirement-the-brief-is-a-file-you-may-copy)
- **Autoplan** — A session plans first, unasked, when its task is large or unclear. Key: `orchestrator.agent.autoplan.enabled`, off by default.
  [spec](../openspec/specs/session-brief/spec.md#requirement-autoplan)
- **Visualize** — A session sketches a task worth a picture before its first edit. Key: `orchestrator.agent.visualize.enabled`, on in the dist.
  [spec](../openspec/specs/session-brief/spec.md#requirement-visualize)
- **Diagnose** — A session diagnoses a bug from facts and a control before code. Key: `orchestrator.agent.diagnose.enabled`, off by default.
  [spec](../openspec/specs/session-brief/spec.md#requirement-diagnose)

## Spec changes · [spec](../openspec/specs/spec-changes/spec.md)

- **Spec change at hand-back** — A hand-back is refused until the task's spec change validates.
  [spec](../openspec/specs/spec-changes/spec.md#requirement-a-hand-back-carries-a-valid-change)
- **Spec fold at ship** — A ship archives the change and folds it into the specs in the same commit.
  [spec](../openspec/specs/spec-changes/spec.md#requirement-a-ship-folds-the-change-into-the-specs)

## Startup · [spec](../openspec/specs/startup/spec.md)

- **Install checks** — A missing part of the installation is refused at start, with no network.
  [spec](../openspec/specs/startup/spec.md#requirement-a-bad-install-is-refused-at-once-offline)
- **One configuration file** — Every option lives in `jagt.yml` alone, described in `jagt.yml.dist`.
  [spec](../openspec/specs/startup/spec.md#requirement-configuration-lives-in-jagtyml)

## Task flow · [spec](../openspec/specs/task-flow/spec.md)

- **One table of statuses** — One table says which action is allowed in which status and where it leads.
  [spec](../openspec/specs/task-flow/spec.md#requirement-one-table-owns-the-life-of-a-task)
- **Verify before hand-back** — jagt runs the project's check command and sends a red run back to the session.
  [spec](../openspec/specs/task-flow/spec.md#requirement-a-hand-back-waits-at-verifying)
- **Deploy check** — Every deploy is checked where it landed, and a failure opens a new round.
  [spec](../openspec/specs/task-flow/spec.md#requirement-every-deploy-is-checked-where-it-landed)
- **Stalled conflict** — A deploy conflict handed to its session that stops moving is yours: one banner, the card says why.
  [spec](../openspec/specs/task-flow/spec.md#requirement-a-stalled-deploy-conflict-is-the-humans)
- **Starting a task** — A task starts from a ticket or a sentence, in its own worktree on its own branch.
  [spec](../openspec/specs/task-flow/spec.md#requirement-starting-a-task)
- **Plan first** — A task asked to plan, or under autoplan, changes no file but its plan until you approve the plan on the board.
  [spec](../openspec/specs/task-flow/spec.md#requirement-plan-first-is-a-gate-jagt-holds)

## Unattended work · [spec](../openspec/specs/unattended-work/spec.md)

- **Auto-review** — jagt polls an open request for comments, only reading and drafting. Key: `orchestrator.autoReview.enabled`, off by default.
  [spec](../openspec/specs/unattended-work/spec.md#requirement-the-auto-review-poll-only-reads-and-drafts)
- **Activity report** — A report of what jagt did unasked is read back from its own log.
  [spec](../openspec/specs/unattended-work/spec.md#requirement-the-activity-report-reads-jagts-own-log)
- **Brakes** — `act` stops after bad endings, a daily ceiling bounds unasked starts, a looping or case-less task waits for your lift.
  [spec](../openspec/specs/unattended-work/spec.md#requirement-act-stops-itself-after-three-bad-endings)
  [spec](../openspec/specs/unattended-work/spec.md#requirement-unattended-starts-have-a-daily-ceiling)
  [spec](../openspec/specs/unattended-work/spec.md#requirement-jagt-stops-a-task-it-suspects-it-is-looping)
  [spec](../openspec/specs/cases/spec.md#requirement-what-no-case-names-is-a-miss)

## Usage limits · [spec](../openspec/specs/usage-limits/spec.md)

- **Limits** — Warned once per period, never gating; a stopped session wakes once at reset. Key: `orchestrator.usageLimits.wakeOnReset`, off.
  [spec](../openspec/specs/usage-limits/spec.md#requirement-a-limit-is-told-once-per-period-never-gated)
  [spec](../openspec/specs/usage-limits/spec.md#requirement-a-stop-on-a-full-window-arms-one-wake)
- **Spend** — A headless read stops at a spend ceiling or a provider outage; a finished task keeps its cost, `stats` ends with the median.
  [spec](../openspec/specs/usage-limits/spec.md#requirement-a-finished-task-keeps-what-it-cost)
  [spec](../openspec/specs/usage-limits/spec.md#requirement-headless-reads-stop-after-repeated-provider-failures)
