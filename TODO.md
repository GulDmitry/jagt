# jagt — TODO / future ideas

Backlog of ideas, not commitments. Structure: what is OPEN first, then the questions nobody has answered yet,
then a compact record of what shipped — kept only where the DECISION is worth re-reading. Invariants live in
CLAUDE.md, not here; if an entry below has hardened into a rule, it belongs there instead.

## Roadmap — what is left (reviewed 2026-08-13)

| # | step | why it earns its place | est. |
|---|------|------------------------|------|
| 1 | MEASURE the CodeHost payoff against a real host | the token drop is still arithmetic, not evidence — one task through one review round with `stats` before/after settles it | 1 h + access |
| 2 | Board tests in a browser (headless chromium), as a fourth CI job | the board has NO automated coverage: columns, action buttons, the SSE update and the ⌘K palette are checked by hand today. A runner already has chromium, so it is the same steps in both pipelines | 1 d |
| 3 | Embed the agent terminal in the board (ttyd) | makes `focus` a click instead of a window switch; a new install requirement, so it goes in README's Prerequisites | 1 d |
| 4 | Rename `review` → `sweep` (keep `review` as a hidden alias) | the command reads as "do a review" but only pulls the pipeline + comments; with `autoReview` polling, the manual trigger is an escape hatch | 2 h |
| 5 | A second `CodeHost` (GitHub) + a `Tracker` seam for the ticket read | both seams have ONE implementation, and `do` spawning a model is the last per-task model cost | 2-3 d |
| 6 | Extend the e2e matrix over `ship`/`review`/`deploy`/`resume` | the matrix covers CREATE→PROVISION→LAUNCH→TEARDOWN; the interesting oracle (status transitions, history, the replies relay) needs a fake `CodeHost` bean and a stub script that reports back over `POST /mcp` | 1-2 d |

Step 1 needs access nobody has handed over yet (a token for a real code host). Everything else is unblocked.

Linux is answered as far as a container and a CI runner can answer it (see the record below); what remains is
one desktop-only question — `reveal` raising the viewer above other applications, and the viewer close that
the container run left open (the `@Disabled` test in `LinuxKittyTerminalDriverLinuxTest` names the lead).

## Open questions

### One task = one repository
A ticket that touches two repos (backend + frontend) has no representation: it becomes two unrelated tasks,
each with its own branch, review request, review cycle and alias, and nothing ties them together — not the
board, not `ship`, not `deploy`. The human keeps the relationship in their head.
A shape question, not a feature: does a task grow a LIST of (project, worktree, branch) tuples, or does jagt
gain a "change set" that groups tasks? The first breaks the "one worktree = one agent" assumption that
`X-Working-Directory` scoping rests on; the second keeps every current invariant and adds a grouping layer,
which is probably the answer — a group's state is then a function of its members' `Move`s.

### Secrets are copied into every worktree and only the happy path cleans them up
`worktree.copyGlobs` deliberately copies gitignored local files — `.env`, `*.pem`, keystores — into each
worktree so the app can actually run there. It means N copies of production-ish credentials in sibling
directories, readable by every agent process, removed only when `done` succeeds in deleting the worktree.
`WorktreeOrphanScanner` REPORTS the leftovers with a count of copied secrets per orphan (startup ping +
`GET /orphans`) and deletes nothing, since an orphan can hold uncommitted work. Two questions still open,
both about the copying itself:
- should the default set be narrower than "any `*.pem`"? One key covers .env, keys, certs and keystores for
  every project.
- `**/.env` does NOT match a root-level `.env` (Java's glob needs a directory component), so a repo whose
  `.env` sits at the top gets nothing copied and the app fails to start for a reason that looks like anything
  but a glob. Either document it in `config.json.dist` or ship `["**/.env", ".env"]`.

### Cycle-time statistics over the status history
`TaskState.history` now holds every transition, so "this ticket spent 6 h waiting on me" and "review rounds
average 3" are cheap to compute. It is a `stats`-shaped question, not a card-shaped one.

### The notification path says nothing about drafted replies
`UserNotifier` fires on the REVIEW_PENDING transition — exactly when `review_replies.md` appears — but the
banner does not mention it, and `postReviewReplies`/`reviewReplyAuthors` stay invisible until a `ship` acts on
them. Both surfaces DO announce the file; only the push notification is silent about it.

### A killed model call is unmeasurable
`ProcessRunner` destroys the process on timeout and throws, so there is no envelope and no usage: the tokens
already burned are unknown, not zero (logged as UNMEASURED rather than guessed). Capturing them needs
`--output-format stream-json` with usage accumulated from the message stream — worth it only if timeouts turn
out to be common, and the 6-minute review sweep is the candidate.

### Review findings not yet fixed (from the full-codebase pass, 2026-08-13)
Ranked; the two that were fixed in that pass (a non-http link reaching the board's `href`, and the
window-elapsed markers leaking one string per task retired while CI_POLLING) are not listed.
- **The board builds three fragments with `innerHTML`/`insertAdjacentHTML`** — the alias/id pair, and the
  project + relative-time + tokens row. Safe only because ids, aliases and project keys are `SAFE_ID`-shaped
  everywhere they enter, which is a coupling invisible at the interpolation site — and `state.json` is
  documented as hand-editable. Build them with `textContent` like the rest of the card. (Left alone because
  `app.js` was mid-edit in another session.)
- **`StateService` re-reads and re-parses the file on every accessor**, so one user action costs several reads
  and a decision can straddle two versions of it (`canonicalTaskId` → `task()` → `tasks()` are three separate
  reads). Writes are atomic and every gate re-checks at execution, so the impact today is a stale MESSAGE, not
  a wrong write. A read cached on (mtime, size) inside the existing lock fixes both, and keeps a hand edit
  visible.
- **The SSE broadcast runs on the caller's thread** — the one serving an agent's MCP call — and sends to every
  browser synchronously, so a stuck tab can add latency to a tool call. A single-thread executor for the
  broadcast decouples them.
- Cosmetic: the board pushes an EMPTY detail `div` when the detail is just the request link (the link is
  rendered separately); `McpProtocolService` can answer `-32603` with a null message when the cause had none.

### What CI found that no local run could (2026-08-13)
Both failures were the same shape — code that assumed the machine it grew up on — and both are fixed with a
RED-verified test. Worth remembering as a class:
- `git merge` exiting non-zero was reported as a merge CONFLICT unconditionally. On a runner with no committer
  identity that meant eight deploy tests "conflicting", and in production it would send a human to resolve
  conflicts that do not exist while LEAVING the deploy worktree behind — so the next `deploy` would take the
  "the human resolved it" path and push whatever was in there. Only unmerged paths mean a conflict now.
- The smoke scripts launched a bare `java` INSIDE a tmux pane, whose shell rebuilds PATH from the system
  profile: a JDK that exists only in the caller's environment (setup-java, sdkman, Nix) is invisible there, so
  the pane printed "command not found" and the script waited for a dashboard that never came. They resolve
  java themselves now — the same lesson as the `/opt/homebrew/bin/tmux` default.
- Also: the process reap hard-failed when `lsof` was missing, taking `done` with it, despite its own javadoc
  promising "never thrown"; and the suite depended on an ambient git identity, so it was green on any
  developer machine and red on every runner. The Test tasks now declare the identity they need.

### Generic wording for the GitLab-leaning internal names (low priority)
`mrUrl` / "MR" / `CI_POLLING` are GitLab-flavoured INTERNAL names. Fine as-is; user-facing text could say
"review request" / "pipeline or checks". Not worth a churny rename until a non-GitLab host is wired.

### A minimal MCP config for the assistant, if determinism ever beats convenience
`--strict-mcp-config --mcp-config <file>` works, but the server names CANNOT be guessed — jagt does not know
whether the tracker is Jira or Linear or what the human named it. It would have to be config
(`assistant.mcpServers: ["…"]`, empty = inherit everything). Given the measured ~7k tokens the MCP surface
costs, this is a determinism nicety, NOT a cost lever.

### Verify what a live agent session does when the backend restarts under it (HTTP transport)
The stdio bridge retried `ECONNREFUSED` for ~15 s, which is what let a session survive a jar rebuild — agents
mark an MCP server as failed on the first error. With Claude pointed straight at the HTTP endpoint that
behaviour belongs to the client, and it is NOT verified: a one-shot `-p` session cannot span a restart.
Measured instead, and worth knowing: a session STARTED while the backend is down has no jagt tools, and the
model then answers "No tasks found." — a lie dressed as data. The sub-agent prompt now forbids that ("if a
`jagt-orchestrator` tool is missing or fails, say the backend is DOWN and stop"), but the reconnect question
needs one interactive session: start an agent, restart the jar, ask it to call `update_agent_status`.
If it turns out a live session does not recover, the fix is not to bring the proxy back for everyone — it is
either a client-side setting or a runtime that keeps the bridge, which is exactly what the seam is for.

## The record — what shipped, and the finding worth keeping

Compact by design: each entry is the decision a future reader would otherwise have to re-derive. The rules
themselves are in CLAUDE.md.

### The screen stopped owning the grammar — 2026-08-14
`MasterShell` was eight collaborators and its test built the whole screen to check that `do ABC-1 from x`
parses. Tier 1 now lives in `shell/GrammarDispatch` (parse a line, run it, fall through to tier 2), the screen
keeps the Lanterna buffer, the input line and Tab completion, and the completion list became a projection
(`TaskViews.choices()` served through `StateViews`) instead of a reach into the MCP facade. 8 → 5
collaborators, and the test went from 31 `mock(` calls to 4 with the layout smoke script still green.

Two more came out of `OrchestratorTools`: `service/DeployService` (the only code that writes a shared branch)
and `mcp/CallerScope` (a call acts on its own task or on nothing, and retiring/deploying/reverting stay with the
human). Both have their own tests now — the scoping rule used to be checked through an eight-mock facade.

### The tools facade is gone, and nothing is over five collaborators — 2026-08-14
`OrchestratorTools` was 871 lines and eleven collaborators, and every attempt to thin it ADDED one: a
delegating aggregate keeps whatever it does not shed, which is why the `deploy` split was reverted the first
time. Dissolving it was the only move that worked.

- The MCP surface became a registry: `mcp/McpTools` + `mcp/McpToolRegistry`, and each group under `mcp/tools`
  declares its own tools with their schemas. `McpProtocolService` takes `List<McpTools>` and knows no tool by
  name, so adding one needs no edit there.
- The work landed in units small enough to test alone: `AgentStatusReports`, `IdeLauncher`, `DeployService`,
  `TaskRetirement`, `TaskResume`, `TicketTitleBackfill`, `WorktreeSetup`, `SubAgentBriefing`, `TaskOperations`,
  and `mcp/CallerScope` for the X-Working-Directory rule.
- `MasterShell` handed the grammar to `shell/GrammarDispatch` (8 → 5 collaborators), `BoardApiController` split
  its writes into `TaskCommandsController` with `RefusedRequests` as shared advice (8 → 5 and 3), and
  `ShipService` dropped its own liveness probe for the one `AgentSessions` already owns (6 → 5).

The numbers, because the tests are the measure: `OrchestratorToolsTest` wrote `mock(` 91 times and no longer
exists; `MasterShellTest` went 31 → 4; the worst file left is 17 across eight tests. 70 classes, none above
five collaborators, 47 at three or fewer.

One coverage hole this opened and closed: moving the deploy tests out took the only assertions that a SUB-AGENT
cannot reach `deploy_task`/`revert_task`/`remove_task`. Testing `CallerScope` alone would have left every tool
free to skip it, so `McpToolScopeTest` drives the declared handlers themselves.

### Comments went through the gate, file by file — 2026-08-14
`sob-ai:commenting` over the infrastructure, the seams and the biggest offenders: `build.gradle` 30 → 10 comment
lines (it explained how the dashboard renders and what a merge conflict means), `application.yml` 55 → 33,
`GitService` 190 → 146, `MasterShell` 140 → 95, `TaskState` 91 → 78, plus `CodeHost`, `TerminalDriver`,
`AbortedConnectionFilter` (24 → 7), `Move`, `McpEndpoint`, `Executables`, `ShipService`, `RunningJarWatch`,
`UsageTracker`, `StubAgentRuntime`, `GitLabCodeHost`, `AbstractKittyTerminalDriver`. 2349 → ~2150 lines against
7271 of code, and the history/justification language ("used to", "which is how", "verified", "Without this") is
gone from every one of the 23 places that carried it. What is left is one-line contract statements — if a sweep
wants a number to chase, chase those instead: a javadoc that repeats the signature is still noise.

### Lombok for the mechanical boilerplate — 2026-08-14
`@RequiredArgsConstructor` in 27 classes (the constructor only assigned), `@Slf4j` for 24 logger fields, `@With`
for 35 positional copy-withers (`ConfigService` 24, `OrchestratorProperties` 11). Findings worth keeping:
- Lombok 1.18.46 (Boot 4.1.0's BOM) compiles under the Java 25 toolchain, and `@With` AND `@Builder` both work
  on RECORDS — the older note claiming otherwise was wrong, which is why it was verified before rewriting.
- Lombok generates constructor parameters in FIELD DECLARATION order, so the conversion was gated on the order
  matching the hand-written one: two same-typed fields in the wrong order would have compiled and been wrong.
- What stays by hand, and why it is not a gap: constructors that validate or derive (`OrchestratorPaths`,
  `PromptTemplates`, `GitLabCodeHost`, `McpProtocolService`, `MacNotifier`, `RestClientJsonHttp`), the second
  constructor a test injects an `Executor` through (`AutoReviewScheduler`, `RunningJarWatch`, `TaskLauncher`),
  every `TaskState` wither (they stamp history and timestamps, not one component), and
  `TaskState.builder(project, worktree, status)` — a generated `builder()` cannot demand those three.

### Hidden work is logged, human commands are not — 2026-08-14
INFO lines for what happens with nobody watching: the auto-review poll firing, what a sweep found, every
instruction relayed into a worktree, every status an agent reports for itself. A button the human pressed is
NOT logged — they were there. Structured via `log.atInfo().addKeyValue(…)` with `logging.structured.format.file
= ecs`, so the file is queryable JSON while the console stays plain text for whoever is reading it live.

### Cost of a headless assistant call — MEASURED 2026-08-12, not guessed
Identical trivial prompt from a temp dir, so these are the per-call FLOOR:

| invocation | input (cache-create) | output | cost |
|---|---|---|---|
| default model (opus), `--setting-sources project` | 38 441 | 60 | **$0.41** |
| `--model haiku`, `--setting-sources project` | 24 869 | 178 | $0.051 |
| `--model haiku`, `--setting-sources user,project,local` (today's default) | 31 719 | 155 | $0.064 |

1. **The model dominates, not the MCP surface** — 6-8x between the inherited default and haiku. Hence
   `orchestrator.assistant.model: haiku` as the shipped default; these are extraction tasks (read a field,
   return JSON under a schema).
2. **~25k tokens is the irreducible baseline** of any `claude -p` process. It cannot be optimized away, only
   AVOIDED by not spawning one — the whole argument for the REST `CodeHost` sweep: 40 polls per request
   × ~$0.40 ≈ $16 per merge request on opus, ≈$2 on haiku, $0 over REST. Each poll pays full cache CREATION;
   the 10-60 min cadence is far outside the prompt-cache TTL, so there is no warm-cache discount to hope for.
3. **The MCP surface costs ~7k (+27%) and is NOT optional** — modern Claude Code defers tool schemas, so the
   7k is the price of having any MCP at all. Narrowing `--setting-sources` saves it and breaks every read:
   the tracker/code-host tools live in USER scope (verified by asking the CLI to name its own `mcp__*` tools
   from the temp dir — `project` alone answers NONE).

`stats` splits the session by `AssistantCallKind` (TICKET_READ / MR_READ / REVIEW_SWEEP / COMMAND_MAP), which
is also how the REST payoff becomes visible per category.

### Mechanical outside ops belong in the backend, not in an LLM tool-loop
Done: the review sweep reads over REST (`CodeHost.readReview` + `GitLabCodeHost`, routed by `ReviewReader`,
which deliberately does NOT fall back to the paid read when a configured host fails — that would spend money
invisibly), the merge request is created/updated in-process, and `ship` commits + pushes itself
(`ShipService`). What stays with the agent is judgement: the code, the ticket distillation, the review replies.
Two deliberate limits: a review-round commit message is mechanical (`<task> address review comments`) because
the backend cannot describe a fix it did not make, and posting the drafted replies needs the thread ids that
`ReviewFacts` does not carry — extending the sweep to carry them is what would finish it.

embabel was investigated and REJECTED for this: it builds an agent that makes in-process LLM calls (Spring AI
+ a GOAP planner), not a controller of external CLI sessions. GOAP is overkill for a ~12-state near-linear
FSM, and it would drag Spring AI + a key into a backend with zero AI dependencies. Revisit only if jagt ever
needs its own reasoning, and even then prefer bare Spring AI.

### Two-tier dispatch — tier 2 is `NaturalLanguageDispatch`
An unknown console line or the board's ⌘K palette goes to a stripped headless call (`--strict-mcp-config` with
an EMPTY server map — text→command needs no tools) that returns `{command, task, ticket, reason}` under a
schema; the dispatcher validates the verb and that the task EXISTS, then executes through `CommandService`, the
same gate the buttons use. Measured at 940 tokens per mapping (stubbed CLI), booked under `COMMAND_MAP`.
Three decisions: the answer leads with the interpretation ("understood as `ship ABC-1` — …") because an
invisible mapping teaches nobody the grammar; ambiguity comes back as a reason rather than a guess between two
tasks; and a single unknown word never reaches the model, because a typo must not cost a call. The model is
handed the projection's own task list with each task's LEGAL actions, so it cannot propose a refused action.
A resident local model was rejected: 4-8 GB for a 3-7B model on a machine that already swaps, versus a
headless call that holds nothing.

### `OrchestratorTools` split — 871 → ~500 lines, 11 → 7 collaborators
Three passes: `ship` → `ShipService`, the worktree file work → `WorktreeFiles` (statics, zero collaborators),
then the one big move — `AgentSessions` (tmux window, focus, kill, relay) + `TaskProvisioning` (worktree
creation, alias, sub-agent context). The shrinkage showed up in the tests, as predicted: the twelve moved
tests build ONE service with four or five collaborators instead of the facade with eleven.

The finding that decided HOW: extracting `deploy`/`prune` (prune has since been removed) into a `RepositoryOps` was tried and reverted
because a delegating facade KEEPS every collaborator it does not shed — that split would have made it twelve
dependencies instead of eleven. Only a group of methods that MONOPOLISES dependencies is worth moving.
Two ideas from that plan stay open and are not worth a pass on their own: `deploy` could still leave,
and `resumeTask` arguably belongs with `TaskLauncher` (it IS a launch).

### `deploy` has an undo — `revert <ticket>`
Reverts the merge commit deploy created on `deployBranch` and pushes the revert; `DEPLOYED` → `REVERTED`, so
no surface keeps claiming the change is live. It only ADDS a commit, and the task branch keeps its commits —
which is why `REVERTED`'s primary move is SHIP (fix and go again), not DONE.
The decision worth keeping: `deploy` merges `--no-ff` and RECORDS the commit (`TaskState.deployCommit`,
including the conflict-resolution path). Without both, "the deploy" is a range of loose commits and reverting
it would undo a fraction of the task. Every ambiguous case is refused with the by-hand `git revert -m 1`
recipe rather than guessed: no recorded commit, not on the branch, not a merge, already reverted (a second
revert would silently re-apply the change), or conflicting with later work there.
NOT offered: re-deploying a REVERTED task — its commits are still in history, so deploy's "nothing to deploy"
guard would refuse anyway. The honest path is a new commit, then ship + deploy.

### Concurrency cap — BUILT, THEN REMOVED 2026-08-13 (do not bring it back)
`agent.maxConcurrentTasks` + `TaskAdmission` capped how many tasks could exist at once, defaulting to 3, on the
strength of a TODO entry about this machine having swapped once. That was the wrong basis: jagt is installed on
other people's machines, and someone with 100 GB of RAM has no business being told three is enough. A limit
jagt cannot derive is a limit jagt should not enforce, so the whole thing is gone — config key, policy class,
the early check in `TaskLauncher`, and the `n/cap` indicator both surfaces showed.
If a bound is ever wanted again it belongs to the human's own tooling, not to a default in this repo.

### Per-task base branch — `do <ticket> from <branch>` (in flight, uncommitted as of 2026-08-13)
A task can be cut from another feature branch, and its review request then targets that branch instead of the
project's `baseBranch` (`deploy` is unaffected — it still merges into `deployBranch`). `TaskState.baseBranch`
stays null when the human named none, so a config change still reaches those tasks. Built by a parallel
session together with the `NewTask`/`LaunchRequest` parameter objects that replace the eight positional Strings
a `do` used to be carried by through four hops. Documented here and in README because the docs went in with an
adjacent commit; the CODE is still in the working tree, so treat this entry as a promise until it lands.

### The projection: `Move` / `Phase` / `Owner` / `TaskView`
The root of "review/ship/deploy are all a blur" was that four statuses all read as the word "review" and the next-step
hint was PROSE, so it could be neither turned into a button nor validated — while `ship`'s real legality lived
somewhere else entirely. Now `Move.forTask(status, hasReviewRequest)` answers phase + owner + legal actions +
the obvious one, `TaskView` carries it to every surface, and `Move.shippable` is the same predicate the ship
gate calls. Twelve statuses collapse into six phases a human reads at a glance:

```
BUILD ──▶    REVIEW ──▶ CHECK ──▶    READY ──▶  DEPLOY ──▶        DONE
NEW          REVIEW_    SHIPPING     REVIEWED   DEPLOYED          DONE
IN_PROGRESS  PENDING    CI_POLLING   APPROVED   DEPLOY_CONFLICT
                        CI_FAILED               REVERTED
🤖 agent     👤 you     🤖/⚙️ CI     👤 you     👤 you            —
```

`TaskStatus` stays the persisted SSOT; `Phase` is a projection, never a second state machine.

### The board — default surface, no build step
Vanilla HTML/CSS/JS in `static/`, no CDN and no external asset of any kind (it must work offline and stay
inside the one jar). SSE push instead of polling — `StateService.onChange` is the single event both surfaces
consume, and the event carries NO payload on purpose, so it cannot disagree with `/api/tasks`. Rejected
alternatives: an IntelliJ plugin (months, and it would bind the UI to one editor against pluggable-by-design),
Electron/native (same cost, more of it), Lanterna mouse support (clicking inside an ASCII table treats the
symptom, not the diagnosis above).

### Status history in `state.json`
`TaskState.history` is append-only `[{status, at}]`, capped at 50 (the file is rewritten on every MCP call).
Two rules make it useful rather than noisy: a keep-alive records nothing (same status → no entry, or four real
transitions would drown in hundreds of identical rows), and a task starts its history at the status it was
CREATED with. `statusSince()` is what the surfaces show — never `lastActiveTimestamp`, which a keep-alive
bumps, making an hour-old status look fresh.

### TUI repaints on state change, not on the timer
`MasterShell` subscribes to the same `StateService.onChange` the board's SSE uses, and the listener only raises
a flag the render loop consumes (Lanterna's screen belongs to the UI thread; the listener runs on whichever
thread served the agent's MCP call). Pinned by `scripts/tui-push-repaint-smoke.sh`: refresh set to 60s, a
status pushed through `POST /mcp`, the screen asserted to show it within seconds — verified RED by deleting
the listener.

### Linux, and testing it from a Mac
The drivers: `LibNotifyNotifier` (`notify-send`), `LinuxKittyTerminalDriver`, and the JetBrains config path no
longer macOS-only. Two findings: driving kitty needed NO Linux-specific code at all (one shared
`AbstractKittyTerminalDriver`, two hooks — `bringToFront` and `platformOptions`, both empty on Linux), and the
editor needed no new class, only the config path fixed.
Then the runner question was answered without a Linux box: `scripts/linux-suite.sh` runs the suites in a
container, and `.github/workflows/ci.yml` / `.gitlab-ci.yml` run the same steps by calling the same scripts.
`linuxDriverTest` is where the drivers meet real binaries — the notification is asserted off the session bus
with `dbus-monitor`, kitty is driven under Xvfb.
That first run paid for itself immediately: `tmux-command` shipped as `/opt/homebrew/bin/tmux`, so EVERY task
on Linux died at "Failed to start command" before its agent started. Binaries are now bare names resolved by
`platform/Executables`.

### The e2e matrix
`./gradlew e2eTest` (own source set, out of `test`/`check`): `TaskFlowCase.matrix()` × `TaskFlowMatrixTest`
runs CREATE→PROVISION→LAUNCH→TEARDOWN over real git/tmux with `orchestrator.agent=stub`, asserting worktree
contents, the per-agent provisioning ABSENCE (a Claude-shaped file in a stub worktree means something outside
the runtime put it there), `TaskStatus`, and that `done` removes the worktree while KEEPING the branch.
Design rules it lives by: assert OBSERVABLE state, never timing; widening coverage is adding a ROW; and a
combination that is not covered is NAMED with the reason. What is still missing is roadmap step 6, plus the
real driver combinations — a GUI cannot be asserted, which is what `linuxDriverTest` exists for instead.

### Seams and their second implementations
`AgentRuntime` (claude / codex / stub) and `TerminalDriver` (kitty / warp) have more than one, and adding
Codex is what MOVED provisioning into the seam — proof that an interface with one implementation had quietly
left `.mcp.json` and the word "Claude" sitting in `OrchestratorTools`. Still single-implementation, so still
unproven: `EditorDriver` (one CLI driver) and `CodeHost` (GitLab only — roadmap step 5). `UserNotifier` got
its second with Linux.
One follow-up Codex left: its worktree gets jagt's MCP proxy but NOT the human's own servers (`CODEX_HOME`
points at the worktree), so such an agent cannot post review replies itself — which stopped mattering when
`ship` moved into the backend.
