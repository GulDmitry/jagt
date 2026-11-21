# jagt — rules

Local orchestration of AI coding-agent CLI sessions across isolated Git worktrees. Java 25 / Spring Boot 4.x,
macOS-first, every OS- and agent-specific piece behind a strategy interface. **This file is `AGENTS.md`**,
`CLAUDE.md` a symlink to it, and nothing here is named after one vendor. Every CLI reads it and reaches the same
MCP server; a root session presents jagt's Master token, so every one is Master. Each CLI's file:
[`docs/rules/components.md`](docs/rules/components.md#whoever-works-on-jagt-reads-the-same-file-and-reaches-the-same-server).

## First priority

**One source of truth, no leaking abstraction, composition pinned by small tests**, above all. A unit knows
what it needs, never who uses it: it names no caller, surface or feature, a comment included (`RingsFitness`).
**jagt is agnostic to everything**: no tracked file names a user's employer, project, ticket, skill, machine
or local setup; such names live only in git-ignored files. **A check holds for all code**: its kind is in
[`ARCHITECTURE.md`](ARCHITECTURE.md#the-kinds) ([0162](docs/decisions/0162-a-check-holds-for-all-code.md)).
**Each sphere's industry standard is jagt's floor** — architecture, code quality, testing, QA, security, operations,
supply chain; a departure only by a decision record ([per kind](ARCHITECTURE.md#the-kinds),
[0180](docs/decisions/0180-each-spheres-industry-standard-is-jagts-floor.md)).

## Where things are written down

| file | holds |
|------|-------|
| `AGENTS.md` (this) | the rules you must not break, and where to read the rest |
| [`openspec/specs/`](openspec/specs/) | what jagt does, as requirements and scenarios |
| `docs/rules/*.md` | each code convention in full, with why |
| `.claude/rules/` | the same table as path-scoped pointers, no rule of its own |
| [`ARCHITECTURE.md`](ARCHITECTURE.md) | the map: what kinds of thing jagt has, and where a new one goes |
| [`README.md`](README.md) + `docs/` | what a human installing jagt needs |
| `backlog/` (local, gitignored) | only what is still open: tasks in order, ideas, owner questions |
| [`docs/roadmap.md`](docs/roadmap.md) | where jagt grows next, in order, and what no step may break |
| [`docs/decisions/`](docs/decisions/README.md) | why an architectural decision holds: its evidence, what reopens it |
| [`docs/features.md`](docs/features.md) | what jagt does, one line each, linked to its spec |

**An architectural decision gets its `docs/decisions/` record in the commit taking it.** **A behaviour change carries its
`openspec/changes/<task>/`** ([`spec-changes`](openspec/specs/spec-changes/spec.md)).
**A new capability adds its line to `docs/features.md`** (`FeaturesTest`).

## Read before you change

| about to touch | read first |
|----------------|------------|
| `flow/`, a status, a capability, an outcome, a task's start | [`task-flow`](openspec/specs/task-flow/spec.md), [`task-start`](openspec/specs/task-start/spec.md) |
| the board, a card, a verb, a report | [`board`](openspec/specs/board/spec.md), [`board-verbs`](openspec/specs/board-verbs/spec.md), [`board-workspace`](openspec/specs/board-workspace/spec.md) |
| a colour, a mark, the colour palette, the legend | [`docs/rules/design.md`](docs/rules/design.md) |
| whose move it is, a badge, an owner, a clock | [`attention`](openspec/specs/attention/spec.md) |
| a notification, a point, a channel, a route | [`notification-routing`](openspec/specs/notification-routing/spec.md) |
| git, `ship`, `deploy`, `revert`, worktrees, multi-repo, a guard | [`git`](openspec/specs/git/spec.md), [`repo-attach`](openspec/specs/repo-attach/spec.md), [`deploy-check`](openspec/specs/deploy-check/spec.md), [`guards`](docs/rules/guards.md) |
| a review round, auto-review, jobs | [`review`](openspec/specs/review/spec.md), [`unattended-work`](openspec/specs/unattended-work/spec.md), [`task-stops`](openspec/specs/task-stops/spec.md) |
| a terminal, tmux, a spawned process | [`runtime`](openspec/specs/runtime/spec.md), [`master`](openspec/specs/master/spec.md) |
| a seam (agent, terminal, editor, OS), the assistant | [`docs/rules/seams.md`](docs/rules/seams.md) |
| a message from a session or a read, a schema, a validation | [`protocol`](openspec/specs/protocol/spec.md) |
| `state.json`, task creation, MCP scoping, startup checks | [`docs/rules/components.md`](docs/rules/components.md) |
| a test, a suite, CI | [`docs/rules/testing.md`](docs/rules/testing.md) |
| a Gradle file, `build-logic/` | [`ARCHITECTURE.md`](ARCHITECTURE.md#the-build) |
| a comment, a doc, a prompt, the shape of a class; a name a discipline already has | [`docs/rules/style.md`](docs/rules/style.md), [`glossary`](docs/rules/glossary.md) |
| a **kind** of thing no rule covers | [`ARCHITECTURE.md`](ARCHITECTURE.md) — if it fits no kind, add a kind |

## Git and committing

- **The base branch is read-only.** Nothing pushes or merges to it, ever.
- **Only `deploy` and its undo `revert` write a shared branch**: `ship` only opens or updates a request; `revert` adds a commit — no force-push.
- A sub-agent pushes **its own task branch** and nothing else.
- **Never `git add -A`** — several sessions share this tree; stage explicit paths only.
- **No git hook in a repository, ever**: never add one, never ask a human to install one, never depend on one.
- **jagt's own hooks are not that**: under `.jagt/` in the worktree jagt cut, that session's alone, the project's
  hooks running underneath. They refuse a push off the task's branch, deleting, branchless, hook-skipping, with
  `-c`, `$'…'` or forced unleased; a plumbing push; `gh`/`glab` but a read; `git worktree add`; a line reaching
  the board or token. Else they report state; gaps: [`guards`](docs/rules/guards.md).
- Never a warning, badge or gate on the deploy confirm: it names the writes and gets out of the way.
- **Commit every finished piece of work in the turn it went green**: left in the tree, it is not delivered.
  Permission to commit is standing, to push is not.
- **One feature, one commit**: its steps, fixes and review findings fold into it (`git commit --amend`);
  an agent branch is squashed on merge.
- **Code review before every commit**, scoped to what *this* session touched ([`docs/rules/style.md`](docs/rules/style.md)),
  and its mutants on the diff when `main` changed ([`testing`](docs/rules/testing.md)).
  **A rejected finding keeps its why where the next review meets it**: a site comment or a Rejected row.
- No review skill to run: read the diff yourself, say so in one line, commit anyway.

## Code

- Gradle, **Groovy DSL only**. Never Maven, never Kotlin, `.kts` included.
- Jackson v3 (`tools.jackson.*`, unchecked); annotations stay `com.fasterxml.jackson.annotation`.
- **The industry-standard library for a job before our own code**: own code only for what is jagt's own, its why in a decision.
- **Three collaborators per class is the target, five the ceiling**, delegates included (`collaborators-per-class`):
  group them into a component, never add a field.
- **A method reads at cognitive complexity 12 at most** (`cognitive-complexity`): split it, never raise the number.
- **No static state, no lifecycle hook that does work**: a static field is final and immutable.
  A holder's inputs are its arguments; a collaborator arrives through the constructor (`collaborator-owners`).
  Start work is an `@EventListener` or a `Job`, never `@PostConstruct`; stop work is a `SmartLifecycle` after the web server
  (`static-state`, `post-construct-hooks`, [0160](docs/decisions/0160-the-chart-is-an-instance-and-start-work-is-an-event.md),
  [0169](docs/decisions/0169-stop-work-is-a-smartlifecycle-after-the-web-server.md)).
- **No duplicated block of 75 tokens or more** (`duplication`): one holder in main, a builder or parameterized case
  in tests. Never raise the number.
- **Checkstyle's default counts gate all code**: 7 parameters, 8 record components; a suppression needs its reason, zero is the target
  ([0143](docs/decisions/0143-checkstyle-gates-parameter-and-component-counts.md)).
- **A compiler or Gradle warning fails the build**: fixed, never suppressed without its reason; only upstream's go in
  `build-logic/accepted-warnings.txt`, each row naming what ends it (`AcceptedWarningsTest`).
- **A test wiring over 5 mocks means the class does too much** (`mocks-per-test`): fix the class, never the fixture.
- **A test shares a `Test<Thing>Builder`, a fake or a file scanner, nothing else**: no helper method or class, no logic in a builder
  ([0163](docs/decisions/0163-a-test-shares-a-builder-a-fake-or-a-scanner.md)).
- **A test builds a class through the constructor production uses**: never a second constructor, an overload or a
  seam in `main` that only tests call (`constructors-called-from-main`).
- **English and UTC only, set in `main`**: no code reads the machine's locale, zone or clock (`now-without-clock`, `BoardAssetsTest`).
  Only the board's UI text may come from a language catalog, English the default
  ([0141](docs/decisions/0141-the-boards-words-are-catalog-files.md)).
- **A new role starts from its `roles/<id>/design.md`**, agreed before its first code ([`ARCHITECTURE.md`](ARCHITECTURE.md)).
- **Nothing below `flow/` decides a status.** `withStatus` lives in `flow/` and the record implementing it (`FlowDoorsFitness`).
- **Only `adapter/` names an OS or a vendor.** `core/` imports no Spring.
- **Every third-party service or technology is a seam chosen in `jagt.yml`**: a port, a driver per vendor, a key with a
  default — never guessed from a name, a URL or a host ([0130](docs/decisions/0130-every-third-party-is-a-configured-seam.md)).
- **jagt's own reach failing is the board's, never a card's**: one mark on the board's head; only a task's own failure stops it.
  Lombok: [0107](docs/decisions/0107-one-lombok-set-core-included.md)'s set only.
- **The board has no build step, no CDN and no external asset** ([`docs/rules/design.md`](docs/rules/design.md)).
- **A capability the board cannot express is a bug**: per-task verbs from `Move.actions()`, global from `GlobalCommand` beans; no surface has a list.
- **No GUI or keystroke automation, ever.** Keystrokes land in whatever is focused.
- **A stop, status or flag names what ends it; one an outside condition set ends on the read that disproves it, and that read runs while it stands.**
- **An ending says which one**: finished, stopped, failed with its cause, or gone; a seam hears every ending
  its source emits ([`seams`](docs/rules/seams.md#an-ending-says-which-one)).
- No positional null-soup: value records get `defaults()` + `withX` withers or a builder.

## Interfaces

- **Nothing is added to a surface without saying what it replaces** — what a mark costs,
  [`board`](openspec/specs/board/spec.md); every mark and the `Help` legend rendering them, [`docs/rules/design.md`](docs/rules/design.md).
- **Never two controls for one question** — a report, a filter and a form included.
- **A fact goes ON the thing it is about**: the verb's own button, the card's own edge.
- **A row carrying four things does not want a fifth**, and **a card must not grow taller**. A fact earning neither goes in the hover.
- **A colour means one thing, board-wide** — meanings in `docs/rules/design.md` alone.
- **What is merely coming is not news.** A countdown belongs in a tooltip; what has STOPPED belongs on screen.
- **Every mark has one row in the legend**, rendered as the page's own element rather than named in words.
- **Getting started stays one copied file and one command.** Options live in `jagt.yml`, a role's own in its folder, each described in its `.dist`.

## Tests

- **Three groups** ([every suite](docs/rules/testing-suites.md)), run as narrow as the change. A task runs its targeted
  tests (`scripts/tests-for.sh`). A batch runs its changed modules, their dependents and the rule tests. A build change
  and a release run everything; the heavy and stand groups run before a minor release's push. A red reruns only what
  failed. **A cached green is not a run.**
- **Before touching a test file, load the unit-testing skill your setup provides, if any**, however small the change.
- **Every fixed bug gets a regression test, verified RED** by reverting the fix and running it. Its commit
  carries a `Defect-family: <A–M>` trailer ([families](docs/rules/testing.md)).
- **Every test pins a business case, and changes only for a reason the commit names**: its scenario changed, or it
  asserted what nothing promised. Never to turn a red run green ([`testing`](docs/rules/testing.md#every-test-pins-a-business-case)).
- A suite that opens a window leaves no trace: `--orchestrator.terminal=none`, a throwaway tmux session, cleanup.
- **No absolute paths in defaults**: an external binary is a bare name, resolved by `adapter/Executables`.

## Writing

- **Write less than feels complete.** Delete every sentence that explains, argues, reassures or names the
  option you rejected — `docs/decisions/` alone keeps it. Before writing a comment, load the commenting skill your setup provides, if any.
- **One fact per line**, in every text: a decision plus one clause of why, never the road to it nor what it used to be.
- **Comments default to none**: one non-obvious WHY at most, never in a test; a multi-line javadoc only for a `core/port` contract.
- **Never cite a line number or a line count** in a doc or a comment; name the file, the symbol or the rule.
- **Budgets, asserted by the `fitness.yml` size rows**, in lines on Anthropic's scale
  ([memory](https://code.claude.com/docs/en/memory), [skills](https://platform.claude.com/docs/en/agents-and-tools/agent-skills/best-practices)):
  a decision 50, a rule 100, a page read whole 200, `ARCHITECTURE.md` 500; no line past 150 characters outside a
  table; comments ≤ 15% of all sources. Past a limit, compress first: review the text,
  cut what restates or explains. Only then split the file one level deep; never raise the number.
  **Where no compression keeps every fact, stop and ask the owner** — never trade a fact for a line.
- **Every log line is structured**: constant event, values as fields, `cause` on every failure, never `{}` or `+`
  in a message. Keys: [`docs/rules/style.md`](docs/rules/style.md).
- **English only**, but for kitty's ЙЦУКЕН keymap, where the symbols are the key events, and the board's language catalogs.
- **Never a real ticket key, project name or issue title**, tests and fixtures included. Invent `ABC-42`.
- Markdown ~120 columns; a non-obvious case earns a spec scenario, not a paragraph.

## The human in the loop

- **One thing other than a human may act as one: the Master session in `act`**, the default
  ([0214](docs/decisions/0214-the-master-acts-by-default.md)): it holds the human's rights but the plan approval and
  the deploy (`roles.master.mine`); the human opts into `judge` or keeps more. Bounded by `FlowRules`, never inventing a move.
- **Nothing else acts unasked but on a stage the tracker or the code host reports**: intake opens a task, the close ends one
  (`ActionOrigin.TRACKER`), an approved request deploys itself (`ActionOrigin.CODE_HOST`, `deploy.onApproval`, on in the server
  starter, [0201](docs/decisions/0201-an-approved-request-deploys-itself-when-deploy-on-approval-is-on.md)); each off by default.
  The auto-review loop reads and drafts, its gate in the outcome rather than the trigger. The base branch stays read-only for all four.
  jagt re-runs a pipeline the host says died on infrastructure, bounded by `infraWait`, and a red deploy pipeline once
  per pushed commit ([0133](docs/decisions/0133-a-red-deploy-pipeline-is-rerun-once.md)). The acceptance workspace request is sixth, off by default
  (`workspace.mergeRequest`): one open request per role on `jagt/acceptance-<role>`, never the base branch, opened through the code-host seam
  ([0198](docs/decisions/0198-the-acceptance-workspace-is-proposed-by-a-request.md)). Nothing else writes to the code host unasked.
  A kept release switches itself only with `releaseFeed.autoUpgrade`, under the launcher (decision 0207).
- **A task opens from a deterministic source alone**: your command or tracker intake, never a model's answer (`TaskOpeningDoorsFitness`).
- **A review round is a judgement, not a work order**: fix the comment, change nothing and say why, or ask — never implement one you think is wrong.
- A blocked session must reach the board: an agent reports `outcome=question` **before** asking a human. A
  task contradicting what the code guarantees is such a question, asked before the code picks a side. It is
  never decided quietly and named in the closing report.
- **What is still open is a list, not a paragraph**: one `OPEN QUESTIONS:` line ending the terminal output
  names what the agent settled unasked. If the answer would have changed the code, it asks instead.
- **Tasks are capped at `maxTasks`, 50 by default, and no bulk branch cleanup** — never a queue, a slots indicator or a `prune` verb.

## Build & run

```sh
cd orchestrator-backend
./gradlew build stageJar
java -jar build/libs/jagt-run.jar          # board on 8290
curl -s localhost:8290/state               # verify
```

> [!IMPORTANT]
> **Run the staged jar**: `./gradlew build` rewrites `jagt.jar` under a running JVM — [why](docs/troubleshooting.md).
