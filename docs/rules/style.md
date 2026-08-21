# Conventions and code quality

[← AGENTS.md](../../AGENTS.md)

## Conventions

### Comments

**Go through the `sob-ai:commenting` skill every time, before writing or editing any comment** — no
exceptions, including a "quick" one-liner. Its hard gate decides: the default is **no** comment, one
non-obvious WHY at most.

Deleted on sight: narration of what the code does, an argument that a change is correct (that belongs in the
review, not in the file), how the code got this way, and a fact whose source of truth is elsewhere.

jagt's own history is the warning: 2349 comment lines against 7027 code lines, a build file explaining how the
dashboard renders and what a merge conflict means, and two comments still naming libraries deleted months
before.

**One more rule for this repo: a file may only speak its own layer.** The build file knows about the build; a
seam interface states its contract and never one implementation's mechanism.

### Every text jagt writes is read by an engineer in a hurry

Shortest form that still answers, lowest cognitive load, no story. One fact per line; a decision is the
decision plus at most one clause of why, not the road to it.

This binds command sentences, docs, prompts and commit messages alike. TODO.md was 670 lines of prose for 40
decisions before it was emptied (2026-08-18), and the owner's complaint was that nobody can read it.

**A decided decision is not a TODO.** It lives in the code, with the rule in this file and the road to it in git
history. TODO.md holds only what is still open, and holding nothing is its normal state. If an entry needs three
paragraphs, the code needs the explanation, not the file.

### The same standard is demanded of the agents

It is one section of `sub-agent-context.md` ("How you write") rather than a clause repeated per artifact: a
status line, a commit message, the review request's title and description, a reply and a code comment are the
same reader in the same hurry.

What sends the agent to the machine's **own** skill is one rule rather than a clause in that section, because
the answer is the same for code, for tests and for a review round as it is for prose: a skill outranks the
brief wherever the machine has one, house style belongs to whoever's machine it is, and jagt can ship none of
it.

Two limits: the brief is written by `initialize_task` alone, so a worktree that already exists keeps the
wording it was created with (recreate the task, or patch its file); and the relay `ship` names the description
rule itself, since that is the one artifact jagt does not write when a host is configured.

### Where a decision is written down

`USE-CASES.md` is the one-line answer per **situation** ("the request does not target the base branch → …").
When a case turns out to be non-obvious — or a session re-derives one that was already decided — append a row
there instead of only fixing the code.

**This file keeps the rules; `USE-CASES.md` keeps the answers.**

### Never use real project identifiers

Anywhere in this repo: code, tests, comments, docs, examples, fixtures. No real ticket keys or numbers, project
names, abbreviations, or issue titles from any actual project.

Always invent obviously fictional placeholders (`ABC-42`, "Widget layout is off"). The existing tests use
`ABC-N` ids — follow that.

### English only, everywhere

UI strings, placeholders, example phrases, comments, docs, test fixtures. The NL palette **accepts** any
language; jagt never **writes** one but English.

The single exception is functional, not textual: `KittyTerminalDriver`'s ЙЦУКЕН keymap (`map=cmd+м …`), where
the Cyrillic symbols **are** the key events.

### Markdown

Aim for ~120-character lines, hard max 150. Do not force awkward wrapping.

### A form field explains itself with a placeholder

Not with a paragraph parked next to its button. The `*-state` spans are progress and verdict slots (`reading
the ticket…`, `no task "x"`) and start **empty** — static prose there vanishes on the first submit (the
`finally` clears it) and never comes back, which reads as a bug.

### Prompt structure

Per Anthropic prompt-engineering guidance, and it applies to every prompt jagt **writes**: the sub-agent
context, the ship and review briefs, the headless assistant prompts.

- Wrap concerns in named XML sections (`<role>`, `<rules>`, `<output_format>`, `<examples>`).
- Forbid preamble explicitly.
- Damp deliberation with "respond directly", never "do not think" — that leaks `<thinking>` tags.
- **Never ask a CLI system prompt for JSON by wording alone** (cost without guarantee). The one place jagt takes
  JSON from a model is the headless assistant, where `--json-schema` actually constrains decoding. Otherwise
  JSON is only for persisted state (`state.json`).

## Code quality

**The test is the litmus of the production code.** If a test needs ~5+ objects set up, or its cognitive load is
high, the smell is in the **production code** (poor decomposition or isolation), not the test. Fix the code so
the test goes light (`sob-ai:unit-testing` §5). Never paper over it with fatter test setup or shared fixtures.

**A test that needs more than ~3 mocks is telling you the class under it does too much.** Fix the class, never
the fixture.

### No god objects

Three collaborators per class is the target, **five is the hard ceiling** — and that ceiling holds for a class
that only **delegates**, because a delegating aggregate is exactly how one grows.

Over it, **group** collaborators into a cohesive component (composition, never inheritance) and let callers
depend on the part they use.

The ceiling is not advisory: `MasterShell` sat at eight and its test built the whole screen to check a parse,
which is how a 31-mock test happens.

**No class is over the ceiling today** (checked 2026-08-14: 70 classes, none above five, 47 at three or fewer).
A new aggregate is how that regresses: when a class would need a sixth collaborator, the answer is a registry of
small units (see `surface/mcp/McpTools`, and `Move.actions()` for the per-task verbs), **never one more field.**

### Records and Lombok

No positional null-soup: config and value records get a builder or `defaults()` + `withX` withers — never a
10-arg record constructor with a row of `null`s.

Lombok carries the **mechanical** boilerplate and nothing else: `@RequiredArgsConstructor` for injected final
fields, `@Slf4j` for the logger, `@With` for a record's positional copy-withers (1.18.46 supports `@With` **and**
`@Builder` on records — verified under the Java 25 toolchain).

Written by hand where the code is not mechanical: a constructor that validates or derives
(`OrchestratorPaths`), a wither that does more than copy one component (`TaskState.withStatus` stamps history),
and `TaskState.builder(project, worktree, status)` — Lombok's generated `builder()` cannot demand those three.

Prefer composition over many injected dependencies. SOLID and clean-code defaults have been standard for 30
years: apply them, do not reinvent them.

### The self-control loop (mandatory, every code + test change)

Run the changed tests through the `sob-ai:unit-testing` skill. If it reports a test as compositionally heavy or
high cognitive load, that is a signal to **refactor the production code** until the test is light — then re-run.

Deliver only when tests are **both** light and green **and** reviewed.

### Code review is mandatory after every code change, before committing

**And it is scoped to what this session touched** — never to "the working diff" and never to the branch.
Several sessions work in this tree at once, so the tree and the index hold their changes too, and
`/code-review` with no target reviews the whole branch since it left the remote **plus** everything
uncommitted, whoever wrote it.

State the scope, and state a **level** every time (the last one typed is remembered and silently applied to the
next call that omits it):

| when | run |
|------|-----|
| before committing | `/code-review medium <the paths you changed>` — the same explicit paths you stage |
| after committing | `/code-review medium <sha>^..<sha>` — a ref range is the only scope another session cannot widen while the review runs |

Stay at `medium` unless the change is genuinely subtle: every level above it fans out eight to ten finder
subagents plus one verifier per candidate location, and each of them re-reads the changed files and the whole of
this file.

Fix every real finding (or explicitly note why it is a non-issue), then re-review if the fixes are non-trivial.
**No commit lands unreviewed** — this is a hard gate, not a suggestion. (A shell hook can only *remind*; it
cannot invoke a skill, so this is enforced here as a workflow rule, not in `settings.json`.)

### Commit every finished piece of work, in the same turn it went green

**Permission to commit is standing; permission to push is not.**

Stage the explicit paths you touched, **never `git add -A`** — other sessions are in this tree.

Where the review skill cannot run (a harness with no subagents), read the diff yourself, say so in one line, and
commit anyway: a change with no logic in it — a string, a doc row, the assertions that follow one — is a diff
read, not a fan-out.

**Work left sitting in the tree is not delivered.**
