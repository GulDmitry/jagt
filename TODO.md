# jagt — TODO / future ideas

Backlog of ideas, not commitments: what is decided but unbuilt, then what is deliberately deferred, then a
compact record of what shipped — kept only where the DECISION is worth re-reading. Invariants live in
CLAUDE.md, not here; if an entry below has hardened into a rule, it belongs there instead.

There is no numbered roadmap any more (reviewed 2026-08-18): every step it carried has shipped, the last being
`review` → `sweep`. Nothing is decided-but-unbuilt right now — what is left is two questions deliberately
deferred, each naming the trigger that would reopen it. Nothing here is vague on purpose: an entry that cannot
say how to do it does not belong.

## Open questions — both DEFERRED, with the trigger that would reopen them

### A killed model call is unmeasurable
`ProcessRunner` destroys the process on timeout and throws, so there is no envelope and no usage: the tokens
already burned are unknown, not zero (logged as UNMEASURED rather than guessed). Capturing them needs
`--output-format stream-json` with usage accumulated from the message stream — worth it only if timeouts turn
out to be common, and the 6-minute review sweep is the candidate. ANSWERED BY: a timeout actually happening
often enough to care — DEFERRED on purpose until one does.

### A minimal MCP config for the assistant, if determinism ever beats convenience
`--strict-mcp-config --mcp-config <file>` works, but the server names CANNOT be guessed — jagt does not know
whether the tracker is Jira or Linear or what the human named it. It would have to be config
(`assistant.mcpServers: ["…"]`, empty = inherit everything). Given the measured ~7k tokens the MCP surface
costs, this is a determinism nicety, NOT a cost lever. ANSWERED BY: someone wanting the determinism enough to
maintain a per-install server list — DEFERRED until then.

## The record — what shipped, and the finding worth keeping

Compact by design: each entry is the decision a future reader would otherwise have to re-derive. The rules
themselves are in CLAUDE.md.

### Ctrl-C on the backend closed every IDE window — 2026-08-18
Reported as "maybe I imagined it", and it was real. `runDetached` was detached in name only: a child stays in
jagt's process group, the terminal sends SIGINT to the whole group, IntelliJ treats SIGINT as a graceful
shutdown, and one IntelliJ process holds every project window. The reason it looked unreproducible is worth
keeping: if the IDE was ALREADY running when `ide` was typed, the launcher hands the path to that instance and
exits, so nothing dies — the symptom only appears when jagt is what started the IDE.

Fixed where the launch happens, not where the shutdown does: the command is wrapped in
`sh -c "trap '' INT QUIT HUP; exec ..."`, since an ignored disposition survives `exec`. Measured both halves
before and after (same pgid; child dead on SIGINT, then alive), and the test sends a real SIGINT rather than
asserting the wrapper's argv.

### Multi-repo `deploy`/`revert` — land in order, stop at the first conflict — 2026-08-18

The refusal is gone: `deploy` merges and pushes repository by repository in the order the task holds them,
recording each merge commit as it lands, and stops at the first conflict. A dry run was rejected before any of it
was written — a shared branch can move between the check and the push, so it would have bought rarity rather than
a guarantee, at the price of merging everything twice.

The four findings worth keeping, each one a thing the plan did not say:
- **"Nothing to deploy" had to stop being a failure**, and review is what caught it: a two-repository task whose
  change only touched one of them landed the first, then died on the second with "no commits beyond dev" —
  status untouched, one side live on a shared branch, and `revert` refusing because the task was never DEPLOYED.
  A dead end for the most ordinary multi-repo shape there is. It is a typed exception now
  (`GitService.NothingToDeployException`), passed over and named, which incidentally makes every "start the
  sequence over" case harmless: a repository already on the branch has nothing to add.
- **Sibling repositories DERIVE THE SAME deploy worktree path** (`<taskId>-deploy`, next to the repository), so
  the directory alone never decides anything: git is asked who cut it (`GitService.hasDeployWorktree`), a merge
  REFUSES to finish a worktree another repository owns — that would push their work to this remote, which is the
  worst outcome in the change and the second review pass is what found it — and only a task handed back at
  DEPLOY_CONFLICT resumes at one, since a leftover from any other round would skip the repositories before it and
  still call the task deployed. Naming the path per project would have been the obvious fix and the wrong one:
  `WorktreeOrphanScanner` and the editor's dead-entry sweep both know that name.
- **A recorded merge commit cannot mean "already landed".** It outlives the round that made it, so a task
  shipped and deployed a second time carries stale commits in every repository — which rules it out both as the
  resume marker and as the source of the half-state (that one is read from where the sequence stopped, or a
  second round's first conflict would announce every repository as live and nothing as missing).
- **`revert` FORGETS each merge commit as it takes it out**, walking backwards. That is what makes a half-failed
  revert repeatable instead of a second guess: the repeat sees only what is still live, and REVERTED waits until
  everything that landed is out.

The single-repository sentences are unchanged to the byte, which is what the e2e flow asserts; the multi-repo
ones name every repository, and the conflict names BOTH sides — live on the deploy branch, and not deployed.

### The editor launcher is a bare name now — 2026-08-18

`editor-command` and `editor-diff-command` shipped an absolute `/Applications/IntelliJ IDEA.app/…` path, against
the rule the rest of jagt follows since `tmux-command` stopped defaulting to a Homebrew prefix. Both default to
`idea` (plus `diff`) and resolve through `Executables`; the absolute path stays as the commented example.

Two things the fix needed beyond the default: only the LAUNCHER of a command list is resolved — an argument is
not a binary — and a launcher nowhere to be found now fails with the config KEY to set, since the alternative is
a spawn error naming a binary the human never typed. What needed no work at all: `ide <task> diff` was already
pointable at any difftool, `[difft]` or `[code, --diff]`, and always had been.

### Orphans went to the log, and the log came back as a report — 2026-08-18

Two changes that are one idea: the thing a human does not act on mid-flight left the board, and the thing they
DO want after an unattended hour arrived — read out of the log jagt already writes rather than out of a new
store.

- **Orphan worktrees are log-only now.** The board button, its dialog, `GET /orphans` and
  `StateViews.orphanedWorktrees` are gone (all of them at once — half a removal is exactly the console-only
  capability parity forbids), and `WorktreeOrphanScanner` WARNs one line per directory with its copied-secret
  count. The startup desktop ping stays: it is for whoever never opens the log. Nothing about the scan changed —
  it still only LOOKS, because an orphan can hold uncommitted work.
- **`activity` is a READER, not a recorder.** `ActivityReport` tails `logging.file.name`, parses the ECS lines
  and keeps the ones carrying a `task` key-value, newest first. Three things it answers instead of showing an
  empty list, because each is a different mistake: no log file configured, a log that is not structured JSON
  (so the fields cannot be read back), and a tail with no task in it at all.
- What made this cheap is a convention that already existed: INFO for work nobody watched, nothing for a button
  a human pressed. Had jagt logged the human's commands too, the report would have been a duplicate of
  `state.json` history rather than the half history cannot show.
- The alternatives were considered and rejected: an in-memory ring buffer loses exactly the overnight trail
  someone opens this for, and a jagt-owned second file is a second answer to "what happened".

### The board's transport is SSE, and the host poll stays a poll — 2026-08-18
Asked whether long polling should become something modern, and the premise needed correcting first: the board
never polled. `web/TaskEventStream` pushes a payload-less Server-Sent Event on `StateService.onChange` and the
page re-fetches `/api/tasks`; the 15s timer in the page only repaints the relative clocks. SSE→WebSocket was
REJECTED: the traffic is one-directional and payload-free, SSE reconnects by itself, and a socket would add a
second protocol for nothing.

What actually polls is outbound — `AutoReviewScheduler` asking the code host every 60s, backing off as a request
ages. Webhooks were REJECTED too, and the reason is not fashion: jagt runs on a laptop with no inbound URL, so a
webhook needs a tunnel or a relay, i.e. infrastructure jagt refuses to require. Poll-only is the decision; revisit
it only if jagt ever grows a hosted half.

### The card labels its second clock — 2026-08-18
`REVIEW_PENDING · 3d` and a bare `4d ago` in one row read as two ages of the same thing. Both numbers are worth
having — one is time in THIS status, the other the session's last activity, which only a keep-alive bumps — so
the fix was presentation: the second is now `active 4d ago`. Dropping it was tried and reverted; the information
is not the problem, the missing word was.

### Copied secrets — `**/.env` is the only default — 2026-08-18
Answered 2026-08-18 (owner): the narrow default wins in both places. `config.json.dist` now ships
`["**/.env"]`, matching `ConfigService.copyGlobsOrDefault()`, so a fresh install and a config with the section
omitted behave identically, and nothing copies key material nobody asked for. Keys, certs and keystores are a
per-project opt-in — the human adds the globs their run configs actually need. What stays open is nothing; the
orphan report still counts what was copied and deletes none of it.

### What CI found that no local run could — 2026-08-13
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

### The sweep is called `sweep`, and the wording stopped naming GitLab — 2026-08-18

`review <task>` is `sweep <task>`: the verb only ever pulled the checks and the unresolved comments, and with
`autoReview` polling it is the escape hatch, not "do a review". Everything a human reads followed — "MR" is a
"review request", a "pipeline" is "checks" — while `mrUrl`, `CI_POLLING` and `SweepResult.Kind.NO_MR` stay as
they are: an internal name costs nothing and a rename of one churns every caller.

The decisions worth keeping:
- **The old spelling still resolves, and is advertised nowhere.** One map owns it (`TaskAction.RENAMED`), and
  every surface where a human TYPES consults it: the console's grammar, the palette (the verb carries its
  retired spellings, matched and offered nowhere) and a tier-2 proposal that echoed the word. Two spellings in
  `help` would be two answers to one question.
- **The buttons needed no change at all.** They come from `Move.actions()` and post `TaskAction.id()`, which is
  what parity is for: the verb was renamed in the enum and both surfaces moved together.
- **`byId` stays STRICT, and the alias lookup resolves retired spellings only.** Two findings from review, and
  the same lesson twice: an alias that leaks into `byId` widens the WIRE surface (`POST …/actions/review` would
  start answering, against that method's own contract), and a console default that resolves any action id turns
  free text into a verb — `diff the last two commits` would have run `diff` on a task called "the" instead of
  reaching the model.
- The first version of that fallback was `byId(...).map(this::act).orElseGet(interpret)` — and a command whose
  answer is null (every mocked one, and any future silent verb) fell through to the MODEL after already
  executing. The test written for the alias is what caught it, before it was ever committed.
- Review also caught the promise being HALF built: with the alias known only to the console, the palette
  answered "“review” is not a command" and then paid a model to map a line the console runs for free — while
  the README said it still worked. A compatibility alias is only as good as the least-informed surface.

`checks` is not a cosmetic swap: GitHub reports check runs, and "pipeline failed" was the word for a thing that
host does not have.

### `resume` reads its request over the host, and the banner names the drafts — 2026-08-18

`CodeHost.readRequest` joined the seam, so the only per-task model call left is gone: `ReviewReader` routes a
request read exactly as it routes a round — configured host first, no fallback to the paid read when a host that
CLAIMED the URL fails — and `TaskResume` takes the reader instead of the assistant. `MergeRequestFacts` moved to
`model`; its `projectPath` went with the move, because nothing ever read it and every host would have had to
produce it. The assistant keeps only what no configured API can do: follow a URL onto a host jagt was never
pointed at. The e2e run therefore doubles `MasterAssistant` as a GUARD rather than a stub — a read that stops
routing through the host now fails the flow instead of quietly paying for it.

The push notification says the drafted replies are waiting, which both surfaces already did and the banner did
not — announced only where the wording does not already carry it, since the advice after a no-change round IS
about posting them.

### One session, many repositories — the flow follows the model — 2026-08-17

`do ABC-1 api,web` (or several picked on the board) cuts a worktree per repository under ONE task and ONE agent
session: the session runs in the first, edits the others in place, and its briefing lists them because the
one-worktree rule it used to read would have made it refuse the work it was given. `ship` then commits, pushes
and opens a request PER repository, each targeting that repository's own base branch; `done` deletes every
worktree, not just the session's — the others hold checkouts and the local files `worktree.copyGlobs` copied in,
and nothing else would ever have removed them.

The decisions worth keeping, each because the obvious alternative is worse:
- **The round is merged, not per repository.** A sweep reads every request and answers as the LEAST finished one:
  approved only when all are, the pipeline reported as the single worst, comments prefixed with the repository
  they came from. Reading only the session's request would let a green half advance the whole task, which is the
  same class of bug as a truncated link — and a status per repository would be a second state machine.
- **A ship is all-or-nothing about the host.** With one repository unhosted the whole task falls back to the
  prose relay: half pushed by jagt and half asked of the agent is a state neither of them can describe.
- **`deploy`/`revert` REFUSED a multi-repo task at first**, because what to do when the second merge conflicts
  after the first is pushed was a decision nobody had made. It was made later the same week — land in order, stop
  at the first conflict, name both sides — and the refusal went with it (see the entry above).
- **The projection grew a list, not a second shape.** `TaskView.repos` always has an entry, so a surface renders
  one card for one repository or three, and the single-repo card looks exactly as it did.
- A creation that fails part way unwinds every worktree it had already cut: a half-created task burns its id, and
  with N repositories it would burn it N times over.

### A live session outlives the backend on the HTTP transport — 2026-08-17

Measured, not argued: a real Claude session in a worktree, pointed at `POST /mcp`, kept working across a jar
restart — the tool call after it answered from the new process, with no reconnect and no prompt. The harsher
case is the one worth knowing: a call made WHILE the backend was down answered "Unable to connect. Is the
computer able to access the url?", and the next call after the restart succeeded, so a failed call does not
retire the server for the session. Nothing depends on the stdio bridge's `ECONNREFUSED` retry any more, and
the reason it holds is on the server side: `/mcp` issues no session id, so there is no state a restart can
invalidate. Keep it that way — a session-bearing transport would put this back on the client's goodwill.

### The e2e matrix reaches past teardown — ship, round, deploy, revert, resume — 2026-08-17

`TaskFlowMatrixTest` only ever asserted CREATE→TEARDOWN, so everything a human actually presses was covered by
unit tests and by hand. There are TWO matrices now, and the split is the decision worth keeping: the viewer
combinations still run creation and teardown four times, while `ReviewRoundCase` × `ReviewAndDeployFlowTest`
runs the flow BETWEEN them once — a round does not vary with how terminals are arranged, and running it four
times over would only pay for that twice.

Three things it does differently from the older matrix, each because the alternative proved weaker:
- The verbs go through the board's own HTTP endpoints instead of the services, so `Move`'s legality gate, the
  refusal path and the ORIGIN stamping are all in the assertion — the history now proves that `board` moved the
  task and `mcp` was the agent, which no unit test can see.
- The agent side is a real `POST /mcp` with the worktree header rather than a call into `AgentStatusReports`.
  That is also what closed the loop the roadmap wanted from a stub script, without a script: the test IS the
  agent, and nothing waits on tmux timing.
- Two doubles only: `FakeCodeHost`, and `MasterAssistant` — the latter because reading a review REQUEST had no
  host seam at all then; once it got one the double stayed as a GUARD, so a flow that pays for a read fails.

It paid for itself on the first run: an MR link longer than one dashboard line was stored TRUNCATED, because the
link was read out of the abbreviated message rather than the one the agent sent — a dead link on the board, and
a URL a configured host then refuses to read. Fixed where the abbreviation is only for the dashboard, with a
RED-verified unit test.

### Three small ones — 2026-08-17
- `StateService.findByWorktree` answers from ANY of a task's repositories. Slice 2 of one-session-many-projects,
  and the promise the model already made in prose.
- The card's last three interpolated fragments are built as DOM (one `span(className, text)`): ids, aliases and
  project keys come out of a file the human is invited to edit, and "they are SAFE_ID-shaped" was a coupling
  invisible from the interpolation site.
- A `**/` prefix in `worktree.copyGlobs` now matches at the repository ROOT too. Java's glob wants a directory
  component there, so the default `["**/.env"]` skipped the `.env` of every single-module repo and the app failed
  to start for a reason that looks like anything but a glob. The rule is ONE helper, shared with the orphan
  report, so what gets copied and what gets counted as left behind cannot drift.

Two the review caught, both worth keeping:
- Copying now SKIPS a path the worktree already has. Whatever the checkout produced is a file git TRACKS, so it
  is not the one the app is missing — and overwriting it starts every worktree with an uncommitted change to a
  tracked file that the agent would commit on `ship`. The root-match widening made that reachable for the exact
  shape it bites: a repo whose root `.env` is committed and whose `.env.local` is the ignored one.
- The orphan report asks every repository of a live task, not just the first — otherwise a multi-repo task gets
  its second worktree announced as rotting, with a secret count and "delete them yourself", while its agent is
  editing it.

And one that needed NO change: a `javascript:` URL cannot reach the board's `href`, because `TaskView.webLink`
drops any non-http(s) link server-side. A second check in the page would have been a second source of truth for
one rule — and the test for it stayed green with the page's own guard removed, which is how that was noticed.

All five RED-verified with the fix reverted.

### The agent's terminal opens inside the board — 2026-08-17
`orchestrator.web-terminal.enabled` (off by default — it is one more binary to install) makes a Focus click open
the task's tmux session in a dialog over the board, writable, so answering an agent's question no longer means
hunting for another window. Decisions worth keeping:
- **One ttyd per tmux SESSION, not per task.** A task is a WINDOW inside the session, so a server per task would
  be N processes and N ports for one view; `focus` selects the window and the panel follows, because a session
  has one current window. The price is that the panel and the native viewer always show the SAME task — the
  per-task alternative needs a grouped session plus a server each, which is not worth it.
- **Not a `TerminalDriver`, and not a sixth seam.** As a driver it would REPLACE kitty/warp instead of adding a
  view, and `focus`'s own sentence ("raised the agents window") would be a lie in a browser. So it is one class
  nothing else names, and a second web terminal is when an interface gets extracted.
- **Writable is the point, and a bound address is NOT the access control** — the finding that mattered, caught in
  review before this shipped. A websocket handshake is exempt from same-origin rules, so with `--writable` alone
  any page the human happens to have open can connect to `ws://127.0.0.1:<port>/ws` and run shell commands in
  the agents' session; loopback is precisely what that page can reach. `--check-origin` closes it and costs the
  panel nothing, because the frame loads ttyd's own page, so its Origin IS the Host. Verified on the real
  binary: a foreign Origin gets the connection dropped, the served page's own gets 101.
  The address still decides who may ask for that page, hence `127.0.0.1` by default. ttyd's `-i` documents an
  interface NAME; libwebsockets 5 also parses a numeric address (verified), so an address ships and the loopback
  name (`lo`, `lo0`) is the documented escape hatch.
- **`--exit-no-conn` instead of reaping.** A server per session that jagt has to clean up needs to hear about
  `done`, which means another collaborator in `AgentSessions` or `TaskRetirement` — both already at five. Let
  the server end with the last viewer instead: no orphan holds a port, and `tab-per-task` cannot accumulate one
  process per task ever focused. The port is then the first FREE one from the configured value, so a ttyd
  orphaned by a `kill -9` moves the next one along rather than disabling Focus until a reboot.
- **The API answers with a PORT, not a URL.** jagt does not know which name the browser reached it under, and a
  URL built from the bind address hands a second machine its own loopback. The page composes
  `http://<its own hostname>:<port>` — which also deleted the "is this bind value a routable host" guesswork
  (`0.0.0.0` had produced an address no browser accepts).
- The frame is unloaded on close, because tmux sizes every window to its SMALLEST attached client: a forgotten
  iframe would shrink the agent's terminal for whoever is actually watching it.
- One deliberate hole: tier 2 (⌘K free text → `/api/interpret`) executes `focus` server-side and answers with a
  sentence, so no panel opens — the button and a parsed `focus a1` both open one. Deciding which verb ran from
  prose would be guessing, and the sentence is the contract there.
- Covered by the browser suite against a stubbed terminal (panel opens, the frame carries the address, closing
  detaches it); the REAL binary was verified by hand — page, origin rules, tmux attach in a live browser, and
  the shutdown reap — but no automated test starts a ttyd, since that needs the binary plus a live session.

### A second code host, and the ticket read stopped costing money — 2026-08-17
`CodeHost` has two implementations (`gitlab`, `github`) and there is a second seam beside it: `Tracker`
(`orchestrator.tracker.type`, `jira` today), routed by `service/TicketReader` the way `ReviewReader` routes a
host. With both wired, a task's whole life costs nothing in model calls except the ⌘K palette (and, until the
request read got a seam of its own, `resume`).

What the second implementation taught, which one never could:
- **A seam must not assume a protocol.** GitHub's review read is one GraphQL query, and not by preference:
  whether a review thread is RESOLVED exists NOWHERE in its REST API, and a round that cannot tell resolved
  from open re-relays every comment it ever saw, every round, forever. One query also makes "a partial read
  must fail whole" trivial.
- **A seam must not assume the API lives where the human does.** GitLab reads its own API under the web root;
  github.com serves it from `api.github.com` and an Enterprise install from `<root>/api/v3`. `base-url` stayed
  the WEB root (it is what decides which URLs a host may claim) and each host derives its endpoints.
- **Two flags turned out to be GitLab's, not "a review request's".** Squash and delete-branch-on-merge are
  REPOSITORY settings on GitHub, so `GitHubCodeHost` writes neither — a `CodeHost` configures no repository.
- **The review MODEL differs, not just the API.** Two GitHub facts the first implementation got wrong, both
  found in review and both RED-verified: a reviewer's substance usually sits in the review BODY rather than in
  inline threads (a round read from threads alone reported "no unresolved comments", and the sweep then advised
  `deploy` while the host was blocking the merge — so review bodies are relayed first and a CHANGES_REQUESTED
  decision can never come back empty), and `reviewDecision` is only populated where the repository REQUIRES a
  review, so an approval on an unprotected repo needs the reviewers' latest states as the fallback — exactly the
  shape of GitLab's EE-only `approved` field needing `approved_by`.
- **Pagination is not optional on a connection that counts what is already done.** GitHub's `reviewThreads`
  includes RESOLVED threads, so a long-lived request with a bot reviewer crosses one page while having three
  open threads; refusing to relay a truncated round would then make that task permanently unreviewable, since
  there is deliberately no paid fallback. It follows the cursor, capped like GitLab's page loop.
- The read/relay format was the one thing worth sharing (`codehost/RelayLine`): an agent must not learn a
  second comment format because the review moved hosts.
- `JsonHttp` moved out of `codehost` into `…http`: a tracker reads over the same transport, and the port was
  never code-host-specific. It is NOT a seventh seam — it exists so every host and tracker is testable with a
  fake instead of a socket.
- Jira is read over the `v2` API deliberately: Cloud and Data Center both serve it, and the three fields a
  launch needs are identical in v2 and v3, so asking for the newer one would drop every self-hosted install
  for nothing. Auth follows what the token IS — with a `user` it is that account's basic credentials (Cloud),
  without one a bearer token (a self-hosted PAT).
- `resume`'s read MOVED into `TaskResume`, which is where it belonged: the request is the only input, so the
  service that resumes should be the one that reads it. That also kept `TaskLauncher` at five collaborators
  when `TicketReader` replaced its assistant — the ceiling doing its job rather than being argued with.
- A conditional-wiring test exists for the same reason `LinuxProfileContextTest` does
  (`OutsideReadsContextTest`): a typo'd `@ConditionalOnProperty` yields NO bean, every read silently falls back
  to the paid one, and the symptom is a bill.

### The board is tested in a browser — 2026-08-17
`./gradlew boardTest` (own source set, out of `check`, a fourth job in both pipelines) boots the app on a random
port and drives the real page: which phases get a column, which buttons a card offers, what a click POSTs, the
refusal wording, the SSE repaint, the palette's client-side verdict, the "waiting on me" filter, the drafted-reply
banner, and that a picked project is sent while an untouched one is not. 17 cases in ~5 s (the terminal panel
brought three more), and three of them were verified RED by breaking the page (dropping the `changed` listener,
rendering empty phases, emptying `STALE_VIEW`).

Decisions worth keeping:
- **Playwright brings its own Chromium** rather than using the host's. The roadmap entry assumed "a runner
  already has chromium" — a runner does, the `eclipse-temurin` image does not, and on Ubuntu the `chromium`
  package is a snap stub, so "install a browser" is not a portable step. A private browser makes a Mac and a
  runner drive the same build; only its shared libraries come from apt, in `scripts/linux-test-deps.sh` (the t64
  names, Ubuntu 24.04+).
- `Playwright.create()` installs EVERY browser it supports (~280 MB) unless told otherwise, so the suite runs
  with `PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1` and a `playwrightChromium` task installs the one it drives.
- The three write paths are mocked (`CommandService`, `TaskLauncher`, `NaturalLanguageDispatch`) — everything
  else, including the projection and the event stream, is the real thing. Asserting through the SERVER (seed
  `StateService`, stub one command, read the DOM) is what keeps these tests about the page rather than about
  JavaScript internals.

### The CodeHost payoff — MEASURED against a real host, 2026-08-17

The same real review rounds swept twice — once with `orchestrator.code-host.type=gitlab`, once without (the
paid headless read) — from a throwaway `ORCHESTRATOR_ROOT` with a stub agent, so no live state was touched.
Model: the shipped `assistant.model: haiku`.

| review round | read path | latency | cost | comments relayed |
|---|---|---|---|---|
| 37 discussions, 9 unresolved | headless | 41.7 s | $0.132 | **5** |
| the same one | REST | 1.5 s | $0 | **9** |
| 4 discussions, 1 unresolved | headless | 29.7 s | $0.058 | 1 |
| the same one | REST | 1.1 s | $0 | 1 |

Three things it settles:

1. **The unit of cost is the review REQUEST, not the call.** The default cadence escalates 10 → 60 min linearly
   across a 24 h window, so it fires ~52 polls — ∫₀^1440 dt/i(t) = (1440/50)·ln 6 — putting one request at
   **$3-$7 on haiku**, 6-8x that on an inherited default model, against $0 over REST. The arithmetic this file
   used to carry (40 polls × $0.40) had the order of magnitude right.
2. **The paid read is LOSSY, and worst on the rounds that matter most.** Nine unresolved comments came back as
   five: it dropped a whole two-note thread and the reviewer's opening question, while REST returned all nine,
   matching the API exactly. That is not merely noise — the sweep ADVANCES a task when the list is empty and CI
   is green, so a lossy read can mark a round REVIEWED with unanswered threads still open. On the 4-discussion
   round it was exact, which is the shape of the risk: loss grows with the size of the round. This is a stronger
   argument for the seam than the money is.
3. **The reader holds up against a real instance**: nested group paths, approval read off `approved`/
   `approved_by`, bot essays truncated, and every outcome seen for real — RELAYED, REVIEWED (clean + green) and
   APPROVED — each under 1.2 s, over four requests in three projects.

Not measured, and deliberately: `createOrUpdateMergeRequest`, the one write. Exercising it means opening a real
merge request, so it stays covered by unit tests only.

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
combination that is not covered is NAMED with the reason. What is still missing is roadmap step 5, plus the
real driver combinations — a GUI cannot be asserted, which is what `linuxDriverTest` exists for instead.

### Seams and their second implementations
`AgentRuntime` (claude / codex / stub) and `TerminalDriver` (kitty / warp) have more than one, and adding
Codex is what MOVED provisioning into the seam — proof that an interface with one implementation had quietly
left `.mcp.json` and the word "Claude" sitting in `OrchestratorTools`. Still single-implementation, so still
unproven: `EditorDriver` (one CLI driver) and `CodeHost` (GitLab only — roadmap step 4). `UserNotifier` got
its second with Linux.
One follow-up Codex left: its worktree gets jagt's MCP proxy but NOT the human's own servers (`CODEX_HOME`
points at the worktree), so such an agent cannot post review replies itself — which stopped mattering when
`ship` moved into the backend.
