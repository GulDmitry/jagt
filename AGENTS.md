# jagt — rules

Local orchestration of AI coding-agent CLI sessions across isolated Git worktrees. Java 25 / Spring Boot 4.x,
macOS-first, every OS- and agent-specific piece behind a strategy interface. **This file is `AGENTS.md`**,
`CLAUDE.md` a symlink to it, and nothing here is named after one vendor. Every CLI reads it and reaches the same
MCP server; no root session carries a worktree header, so every one is Master. Which file each CLI reads it
through: [`docs/rules/components.md`](docs/rules/components.md#whoever-works-on-jagt-reads-the-same-file-and-reaches-the-same-server).

## Where things are written down

| file | holds |
|------|-------|
| `AGENTS.md` (this) | the rules you must not break, and where to read the rest |
| `docs/rules/*.md` | each rule in full, with why. **Read the one that covers what you are about to change.** |
| `.claude/rules/` | the same table as path-scoped pointers, and no rule of its own |
| [`ARCHITECTURE.md`](ARCHITECTURE.md) | the map: what kinds of thing jagt has, and where a new one goes |
| [`USE-CASES.md`](USE-CASES.md) | one line per situation, already decided |
| [`README.md`](README.md) + `docs/` | what a human installing jagt needs |
| [`TODO.md`](TODO.md) | only what is still open. Holding nothing is its normal state |

A decided decision is not a TODO: it lives in the code, the rule in `docs/rules/`, the road to it in git history.

## Read before you change

| about to touch | read first |
|----------------|------------|
| `flow/`, a status, a capability, an outcome | [`docs/rules/flow.md`](docs/rules/flow.md) |
| the board, a card, a verb, a report | [`docs/rules/surfaces.md`](docs/rules/surfaces.md) |
| a colour, a mark, the colour palette, the legend | [`docs/rules/design.md`](docs/rules/design.md) |
| whose move it is, a badge, an owner, a clock | [`docs/rules/attention.md`](docs/rules/attention.md) |
| git, `ship`, `deploy`, `revert`, worktrees, multi-repo | [`docs/rules/git.md`](docs/rules/git.md) |
| a review round, drafted replies, auto-review, jobs | [`docs/rules/review.md`](docs/rules/review.md) |
| a terminal, tmux, a spawned process | [`docs/rules/runtime.md`](docs/rules/runtime.md) |
| a seam (agent, terminal, editor, OS), the assistant | [`docs/rules/seams.md`](docs/rules/seams.md) |
| `state.json`, task creation, MCP scoping, startup checks | [`docs/rules/components.md`](docs/rules/components.md) |
| a test, a suite, CI | [`docs/rules/testing.md`](docs/rules/testing.md) |
| a comment, a doc, a prompt, the shape of a class | [`docs/rules/style.md`](docs/rules/style.md) |
| a **kind** of thing no rule covers | [`ARCHITECTURE.md`](ARCHITECTURE.md) — if it fits no kind, add a kind |

## Git and committing

- **The base branch is read-only.** Nothing pushes or merges to it, ever.
- **Only `deploy` and its undo `revert` write a shared branch**: `ship` only opens or updates a request; `revert` adds a commit — no force-push.
- A sub-agent pushes **its own task branch** and nothing else.
- **Never `git add -A`** — several sessions share this tree; stage the explicit paths you touched.
- **No git hook in a repository, ever**: never add one, never ask a human to install one, never depend on one.
- **jagt's own hooks are not that**: under `.jagt/` in the worktree jagt cut, reached by that session alone,
  refusing exactly one thing — a push to anything but the task's own branch — with the project's hooks still
  running underneath; an agent CLI's hooks answer that refusal and report session state, and nothing else is gated.
- Never add a warning, a badge or a gate to the deploy confirm. It names the writes and gets out of the way.
- **Commit every finished piece of work in the turn it went green**; permission to commit is standing, to push is not.
- **Code review before every commit**, scoped to what *this* session touched ([`docs/rules/style.md`](docs/rules/style.md)).
- Where the review skill cannot run, read the diff yourself, say so in one line, and commit anyway.
- **Work left sitting in the tree is not delivered.**

## Code

- Gradle, **Groovy DSL only**. Never Maven, never Kotlin — including `.kts`.
- Jackson v3 (`tools.jackson.*`, unchecked); annotations stay `com.fasterxml.jackson.annotation`.
- **Three collaborators per class is the target, five the ceiling**, delegates included: group them into a component, never add a field.
- **A test needing more than ~3 mocks means the class does too much.** Fix the class, never the fixture.
- **Nothing below `flow/` decides a status.** `withStatus` lives in `flow/` and the record implementing it.
- **Only `adapter/` names an OS or a vendor.** `core/` imports no Spring and no Lombok.
- **The board has no build step, no CDN and no external asset** ([`docs/rules/design.md`](docs/rules/design.md)).
- **A capability the board cannot express is a bug**: per-task verbs from `Move.actions()`, global from `GlobalCommand` beans; no surface has a list.
- **No GUI or keystroke automation, ever.** Keystrokes land in whatever is focused.
- No positional null-soup: value records get `defaults()` + `withX` withers or a builder.
- Lombok carries the mechanical boilerplate only.

## Interfaces

- **Nothing is added to a surface without saying what it replaces** — what a mark costs,
  [`docs/rules/surfaces.md`](docs/rules/surfaces.md); every mark and the `Help` legend rendering them, [`docs/rules/design.md`](docs/rules/design.md).
- **Never two controls for one question** — a report, a filter and a form included.
- **A fact goes ON the thing it is about**: the verb's own button, the card's own edge.
- **A row carrying four things does not want a fifth**, and **a card must not grow taller** — a fact earning neither goes in the hover.
- **A colour means one thing, board-wide** — the meanings live in `docs/rules/design.md` and nowhere else.
- **What is merely coming is not news.** A countdown belongs in a tooltip; what has STOPPED belongs on screen.
- **Every mark has one row in the legend**, rendered as the page's own element rather than named in words.
- **Getting started stays one copied file and one command.** Every option lives in `jagt.yml` and nowhere else, described in `jagt.yml.dist`.

## Tests

- The gate is `cd orchestrator-backend && ./gradlew test`; `e2eTest`, `boardTest`, `linuxDriverTest` and `promptEval` are asked for by name.
- **Load `sob-ai:unit-testing` before touching any test file**, however small the change.
- **Every fixed bug gets a regression test, verified RED** by reverting the fix and running it.
- Run `./gradlew boardTest` after any change to `static/`.
- A suite that opens a window leaves no trace: `--orchestrator.open-terminal-window=false`, a throwaway tmux session, cleanup.
- **No absolute paths in defaults**: an external binary is configured by bare name and resolved by `adapter/Executables`.

## Writing

- **Write less than feels complete.** Delete every sentence that explains, argues, reassures or names the
  option you rejected. Load `sob-ai:commenting` before writing a comment.
- **One fact per line**: a decision plus one clause of why, never the road to it nor what it used to be — in
  comments, docs, prompts, output and commit messages alike.
- **Comments default to none**: one non-obvious WHY at most, never in a test; a multi-line javadoc only for a `core/port` contract.
- **Never cite a line number or a line count** in a doc or a comment — it is wrong at the next edit; name the file, the symbol or the rule.
- **Budgets, asserted by `TextBudgetTest`**: a `docs/rules/` file ≤ 700 words, a guide ≤ 1200, no line over 220
  characters, comments ≤ 15% of main sources. Counted in WORDS — a paragraph folded into a table cell is the
  same paragraph. Over budget means cut text; raising a number is the owner's call.
- **Every log line is structured**: constant event message, values as fields, `cause` on every failure. Never
  `{}` in a message, never `+` into one. Keys: [`docs/rules/style.md`](docs/rules/style.md).
- **English only.** The one exception is kitty's ЙЦУКЕН keymap, where the symbols are the key events.
- **Never a real ticket key, project name or issue title**, tests and fixtures included. Invent `ABC-42`.
- Markdown ~120 columns, hard max 150. A non-obvious case earns a one-line `USE-CASES.md` row, not a paragraph.

## The human in the loop

- jagt never commits to a shared branch, opens a review request, deploys, or posts a reply **on its own**.
- **The auto-review loop only reads and drafts** — never ships, deploys, pushes or posts; the gate is in the outcome, not the trigger.
- **A review round is a judgement, not a work order**: fix the comment, change nothing and say why, or ask — never implement one you think is wrong.
- A blocked session must reach the board: an agent reports `outcome=question` **before** asking a human, and a
  task contradicting what the code guarantees is such a question, asked before the code picks a side — never
  decided quietly and named in the closing report.
- **What is still open is a list, not a paragraph**: one `OPEN QUESTIONS:` line ends the session's terminal
  output with what the agent settled unasked. If the answer would have changed the code, it asks instead.
- **No limit on concurrent tasks, and no bulk branch cleanup** — never a cap, a queue, a slots indicator or a `prune` verb.

## Build & run

```sh
cd orchestrator-backend
./gradlew build stageJar
java -jar build/libs/jagt-run.jar          # board on 8290
curl -s localhost:8290/state               # verify
```

> [!IMPORTANT]
> **Run the staged jar.** `./gradlew build` rewrites `jagt.jar` in place, and a JVM reading it dies with a
> `NoClassDefFoundError` that masks the real error — [why](docs/troubleshooting.md#startup-and-the-jar).
