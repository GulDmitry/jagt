# jagt — TODO

One line per decision. Rules live in CLAUDE.md; situations in USE-CASES.md.

## Open

Nothing. A second `Tracker` implementation was declined 2026-08-18 (the seam reads three fields of one ticket,
so the risk it is wrong is small).

## Decided

Newest first. Where a decision hardened into a rule, the rule is in CLAUDE.md and this is only the date.

### 2026-08-18
- `assistant.mcp-config` declares the servers a read may load instead of inheriting the human's own. No
  extraction needed: the servers were already a plain file with `${ENV}` placeholders and no secrets in it, so
  jagt points at it and still holds no credential. It buys determinism and NOT money — measured cold, $0.09
  against $0.04, because the inherited config rides the prompt cache the human's own sessions keep warm and a
  jagt-private prefix is cold on almost every call. It pins the servers ONLY: dropping the settings with them
  would break the `${ENV}` placeholders the safety of the whole shape rests on. One value, not a list — a list
  would be bound by splitting an inline declaration on commas.
- A killed model call stays unmeasured: `--output-format stream-json` was declined. The log already says the
  cost is unknown, which is the whole of what a human does with it.
- Board binds `127.0.0.1`: no password, and it can deploy, close tasks and start agents. Widening is config.
- One run, one log (`ui/SessionLog`): `activity` reads the log back, so older runs would read as today's work.
  Never clears a file a running instance owns — the port is probed first.
- `activity` reads jagt's own ECS log instead of a second store (in-memory would lose the overnight trail).
- Orphan worktrees left the UI entirely (button, `/orphans`, view) and are a startup WARN plus one ping.
- Multi-repo `deploy`/`revert`: land in order, stop at the first conflict, name BOTH sides. A dry run was
  rejected — a shared branch can move between check and push, so it buys rarity, not a guarantee.
  Four things the plan missed: "nothing to deploy" is a skip not a failure; siblings derive the SAME
  `<taskId>-deploy` path, so git is asked who owns it; a recorded merge commit outlives its round, so it cannot
  mean "landed"; `revert` forgets each commit as it takes it out, which makes a half-failed undo repeatable.
- A branch the base repository holds is freed, not refused: detached in place, undone if creation then fails.
- `runDetached` gets its own session (`setsid`, `perl` on macOS). The first fix ignored INT/QUIT/HUP, and that
  disposition is inherited — it would have killed the IDE's own Stop button. A wrapper that always starts also
  means a missing binary must be caught by exit code, or "ttyd absent" reads as "not configured".
- Editor launchers are bare names resolved by `Executables` (which now also looks inside app bundles).
- `review` → `sweep`, old spelling accepted where a human types and advertised nowhere. "MR" → "review request",
  "pipeline" → "checks" in every sentence a human reads; internal names untouched.
- Every human-facing sentence got shorter: `<verb> <task>: <what happened>; <state> — <your next move>`.
- `resume` reads its request over `CodeHost.readRequest`, so no per-task model call is left.
- The board's transport stays SSE (one-directional, payload-free) and the host poll stays a poll: jagt has no
  inbound URL, so webhooks would need infrastructure it refuses to require.
- `worktree.copyGlobs` defaults to `**/.env` in both the dist file and the code fallback.
- The card labels its second clock (`active 4d ago`) instead of showing two bare ages.
- Version 0.6.0; CI keeps the jar as an artifact and attaches it to a release only on a `v*` tag.

### 2026-08-17
- One session, many repositories: a worktree each, `ship` per repository, ONE merged review round answering as
  the least finished one.
- A live agent session survives a backend restart on the HTTP transport (measured), because `/mcp` issues no
  session id.
- The e2e suite reaches past teardown: two matrices — viewer combinations for create/teardown, one combination
  for ship → round → deploy → revert → resume, driven through the board's own endpoints.
- The board is tested in a real browser (`boardTest`), asserting through the server, never by evaluating JS.
- The agent's terminal opens inside the board (one ttyd per tmux SESSION, `--check-origin`, `--exit-no-conn`).
  A bound address is NOT the access control — a websocket handshake ignores same-origin.
- A second `CodeHost` (GitHub) and a `Tracker` seam (Jira): a configured read costs no model call, and neither
  falls back to the paid read when a host that claimed the URL fails.
- Measured payoff of a configured host: the paid read returned 5 of 9 comments, the REST read all 9.
- Three small ones: `findByWorktree` answers from any repository of a task; card fragments are built as DOM;
  `**/` in a copy glob matches at the repository root too.

### 2026-08-14
- `MasterShell` no longer owns the grammar — `shell/GrammarDispatch` parses and runs, the screen paints.
- `OrchestratorTools` dissolved: each MCP tool group declares its own tools, nothing is over five collaborators.
- Comments went through the gate file by file (`GitService` 190 → 1xx lines, `build.gradle` 30 → 10).
- Lombok carries the mechanical boilerplate only (`@RequiredArgsConstructor`, `@Slf4j`, `@With`).
- Hidden work is logged, human commands are not — which is what makes `activity` readable at all.

### 2026-08-13 and earlier
- A concurrency cap was built and REMOVED: jagt runs on other people's machines, so a number picked here is
  wrong for almost everyone. Do not reintroduce it.
- Per-task base branch (`do <ticket> from <branch>`): moves what the worktree is cut from and what the request
  targets, never what a deploy merges into.
- `deploy` has an undo (`revert`): reverts the recorded merge commit, adds a commit, never rewrites history.
- The projection (`Move`/`Phase`/`Owner`/`TaskView`) is what both surfaces render, so a verb cannot exist in one.
- Status history in `state.json`: every transition with WHO asked, capped at 50, keep-alives record nothing.
- The TUI repaints on `StateService.onChange`, not on the timer — the same event the board's SSE carries.
- Linux needed no Linux-specific kitty code; the two platform subclasses differ by `bringToFront` and options.
- Two CI findings worth remembering as a class: a non-zero `git merge` is not always a conflict, and a bare
  `java` inside a tmux pane loses the caller's JDK.
- Cost of a headless assistant call, measured: ~$0.41 on the inherited default vs ~$0.06 on haiku, ~25k input
  tokens of CLI baseline either way. The lever is fewer calls, not shorter prompts.
- Mechanical outside operations belong in the backend, not in an LLM tool-loop: the sweep reads over REST and
  `ship` opens the request in-process.
- Tier 2 (`NaturalLanguageDispatch`) proposes ONE grammar command and executes it through the same gate a button
  uses; the call carries no MCP servers.
