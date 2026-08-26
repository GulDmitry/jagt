# jagt — rules

Local orchestration of AI coding-agent CLI sessions across isolated Git worktrees. Java 25 / Spring Boot 4.x,
macOS-first, with every OS- and agent-specific piece behind a strategy interface.

**This file is `AGENTS.md`**; `CLAUDE.md` is a symlink to it. No file here is named after one vendor — a rule
written into a vendor-named file binds one session in three.

## Where things are written down

| file | holds |
|------|-------|
| `AGENTS.md` (this) | the rules you must not break, and where to read the rest |
| `docs/rules/*.md` | each rule in full, with why. **Read the one that covers what you are about to change.** |
| [`ARCHITECTURE.md`](ARCHITECTURE.md) | the map: what kinds of thing jagt has, and where a new one goes |
| [`USE-CASES.md`](USE-CASES.md) | one line per situation, already decided |
| [`README.md`](README.md) + `docs/` | what a human installing jagt needs |
| `TODO.md` | only what is still open. Holding nothing is its normal state |

A decided decision is not a TODO: it lives in the code, the rule in `docs/rules/`, the road to it in git history.

## Read before you change

| about to touch | read first |
|----------------|------------|
| `flow/`, a status, a capability, an outcome | [`docs/rules/flow.md`](docs/rules/flow.md) |
| the board, a card, a verb, a report | [`docs/rules/surfaces.md`](docs/rules/surfaces.md) |
| whose move it is, a badge, an owner, a clock | [`docs/rules/attention.md`](docs/rules/attention.md) |
| git, `ship`, `deploy`, `revert`, worktrees, multi-repo | [`docs/rules/git.md`](docs/rules/git.md) |
| a review round, drafted replies, auto-review, jobs | [`docs/rules/review.md`](docs/rules/review.md) |
| a terminal, tmux, a spawned process | [`docs/rules/runtime.md`](docs/rules/runtime.md) |
| a seam (agent, terminal, editor, OS), the assistant | [`docs/rules/seams.md`](docs/rules/seams.md) |
| `state.json`, task creation, MCP scoping, startup checks | [`docs/rules/components.md`](docs/rules/components.md) |
| a test, a suite, CI | [`docs/rules/testing.md`](docs/rules/testing.md) |
| a comment, a doc, a prompt, the shape of a class | [`docs/rules/style.md`](docs/rules/style.md) |

`.claude/rules/` carries the same table as path-scoped pointers, so a Claude session is reminded when it opens
one of those files. They hold no rule of their own — this table is the answer every CLI gets.

Adding a **kind** of thing no rule covers: read `ARCHITECTURE.md` first. If it fits no kind, the kind is
missing — add a kind, never an exception.

## Git

- **The base branch is read-only.** Nothing pushes or merges to it, ever.
- **The only writes to a shared branch are `deploy` and its undo `revert`.** `ship` opens or updates a review
  request; it never merges. `revert` adds a commit — no history rewrite, no force-push.
- A sub-agent pushes **its own task branch** and nothing else.
- **Never `git add -A`.** Several sessions share this tree — stage the explicit paths you touched.
- **No git hooks, ever.** Never propose, add or rely on one; enforce invariants in code and prompts. An
  agent CLI's own hooks are a different thing: they report a session's state, and they refuse exactly one thing
  — a push to anything but the task's own branch. Nothing else is ever gated there.
- Never add a warning, a badge or a gate to the deploy confirm. It names the writes and gets out of the way.

## Committing

- **Commit every finished piece of work in the same turn it went green.** Permission to commit is standing;
  permission to push is not.
- **Code review is mandatory before every commit**, and scoped to what *this* session touched — never the
  working diff, never the branch:

  | when | run |
  |------|-----|
  | before committing | `/code-review medium <the paths you changed>` |
  | after committing | `/code-review medium <sha>^..<sha>` |

  Stay at `medium` unless the change is genuinely subtle. Fix every real finding or say why it is not one.
- Where the review skill cannot run, read the diff yourself, say so in one line, and commit anyway.
- **Work left sitting in the tree is not delivered.**

## Code

- Gradle, **Groovy DSL only**. Never Maven, never Kotlin — including `.kts`.
- Jackson v3 (`tools.jackson.*`, unchecked); annotations stay `com.fasterxml.jackson.annotation`.
- **Three collaborators per class is the target, five is the hard ceiling** — including a class that only
  delegates. Over it, group them into a component; never add one more field.
- **A test needing more than ~3 mocks means the class does too much.** Fix the class, never the fixture.
- **Nothing below `flow/` names a status.** `withStatus` appears in `flow/` and in the record that implements
  it, nowhere else.
- **Only `adapter/` names an OS or a vendor.** `core/` imports no Spring and no Lombok.
- **The board has no build step, no CDN and no external asset of any kind** — it must work offline, inside the
  one jar.
- **A capability that exists in one surface only is a bug.** Per-task verbs come from `Move.actions()`; global
  verbs are `GlobalCommand` beans. Neither surface holds a list.
- **No GUI or keystroke automation, ever.** Keystrokes land in whatever is focused.
- No positional null-soup: value records get `defaults()` + `withX` withers or a builder.
- Lombok carries the mechanical boilerplate only.

## Interfaces

**Nothing is added to a surface without saying what it replaces.** A board is read at a glance or it is not
read at all, and design is what a human pays for in attention. Full rules and the reasoning:
[`docs/rules/surfaces.md`](docs/rules/surfaces.md) — read it before you put anything on the board.

- **Never two controls for one question.** "How does this work" already has a button; a colour legend is a
  section of it, never a second button beside it. The same goes for a report, a filter and a form.
- **A fact goes ON the thing it is about** — the verb's own button, the card's own edge — never as one more
  mark beside the others for a human to find and match up.
- **A row that already carries four things does not want a fifth**, and **a card must not grow taller** to
  hold one. Height and cognitive load are the same budget spent twice; a fact that earns neither goes in the
  hover.
- **A colour means one thing, board-wide** — green is a step already taken (work on a shared branch, a deploy
  that ran, a request approved), violet the reviewers, red broken, amber your move. Colour is the cheapest mark
  and the easiest to overspend: a second meaning is read wrong before anyone looks it up.
- **What is merely coming is not news.** A countdown belongs in a tooltip; what has STOPPED belongs on screen.
- Every mark has one row in the legend, and the legend renders the page's **own** element rather than naming a
  colour in words.
- **Getting started must stay one copied file and one command.** Configuration lives in `jagt.yml` and nowhere
  else — never a second file, a hidden override or an undocumented default. Every option an engineer can set
  is in `jagt.yml.dist` with what it means; anything not worth explaining there is not worth having.

## Tests

- The gate is `cd orchestrator-backend && ./gradlew test`. `e2eTest`, `boardTest` and `linuxDriverTest` are
  asked for by name — each needs a machine a hermetic run must not depend on.
- **Load `sob-ai:unit-testing` before touching any test file**, however small the change.
- **Every fixed bug gets a regression test, verified RED** by reverting the fix and running it.
- Run `./gradlew boardTest` after any change to `static/`.
- A suite that opens a window must leave no trace: `--orchestrator.open-terminal-window=false`, a throwaway
  tmux session, cleanup afterwards.
- **No absolute paths in defaults.** An external binary is configured by bare name and resolved by
  `adapter/Executables`.

## Writing

- **Load `sob-ai:commenting` before writing or editing any comment.** The default is **no** comment; one
  non-obvious WHY at most. A file may only speak its own layer.
- **Every log line is structured, never interpolated**: `log.atWarn().setMessage("read failed")
  .addKeyValue("url", url).addKeyValue("cause", why).log()` — constant event, values as fields, `cause` on
  every failure. Never `{}` in a message, never `+` into one. Grammar and keys:
  [`docs/rules/style.md`](docs/rules/style.md).
- **Every text jagt writes is read by an engineer in a hurry**: one fact per line, a decision plus at most one
  clause of why — never the road to it. This binds command sentences, docs, prompts and commit messages alike.
- **English only, everywhere.** The NL palette *accepts* any language; jagt *writes* nothing but English. The
  one exception is functional: kitty's ЙЦУКЕН keymap, where the symbols are the key events.
- **Never a real ticket key, project name or issue title** — anywhere, including tests and fixtures. Invent
  `ABC-42`.
- Markdown ~120 characters per line, hard max 150.
- When a case turns out to be non-obvious, append a row to `USE-CASES.md` instead of only fixing the code.

## The human in the loop

- jagt never commits to a shared branch, opens a review request, deploys, or posts a reply **on its own**.
- **The auto-review loop only reads and drafts.** It never ships, deploys, pushes or posts. Do not erode this:
  the gate lives in the outcome, not in who triggered the sweep.
- **A review round is a judgement, not a work order.** Fix the comment, change nothing and say why, or ask —
  never implement a suggestion you think is wrong.
- A blocked session must reach the dashboard. An agent reports `outcome=question` **before** putting any
  question to a human — and a task that **contradicts** what the code already guarantees is such a question,
  asked before the code picks a side, never decided quietly and named in the closing report.
- **What is still open is a list, not a paragraph.** What the agent settled without an answer and the human
  still has to know goes under one `OPEN QUESTIONS:` line ending the session's terminal output. It never
  replaces `outcome=question`: if the answer would have changed the code, the agent asks.
- **No limit on concurrent tasks, and no bulk branch cleanup.** Both were built and removed on the owner's
  instruction. Do not reintroduce a cap, a queue, a slots indicator or a `prune` verb.

## Build & run

```sh
cd orchestrator-backend
./gradlew build stageJar
java -jar build/libs/jagt-run.jar          # board on 8290
curl -s localhost:8290/state               # verify
```

> [!IMPORTANT]
> **Run the staged jar.** `./gradlew build` rewrites `jagt.jar` **in place** (same inode), so a JVM running
> from it corrupts its class loading: the first not-yet-loaded class dies with `NoClassDefFoundError` — which
> then masks the real error — and a still-running instance answers 500 on the endpoints it had not served yet.
> It is not a code bug. Do not "fix" it by preloading classes; restart from the freshly staged jar.

## Working on jagt with any agent CLI

| CLI | reads | reaches jagt's MCP through |
|-----|-------|----------------------------|
| Claude | `AGENTS.md` via the `CLAUDE.md` symlink | `.mcp.json` (HTTP) |
| Codex | `AGENTS.md` natively | `.codex/config.toml` (stdio bridge; start it at the repository root) |
| Qwen | `AGENTS.md` via `.qwen/settings.json` | `.qwen/settings.json` (HTTP) |

None carries a worktree header, so every root session is Master.
