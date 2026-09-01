# Conventions and code quality

[← AGENTS.md](../../AGENTS.md)

## Writing

- The `sob-ai:commenting` gate deletes on sight: narration of what the code does, an argument that a change
  is correct, how the code got this way, a fact whose source of truth is elsewhere.
- **`USE-CASES.md` is the one-line answer per situation**: "the request does not target the base branch → …".
- No real ticket number or abbreviation either; tests use invented `ABC-N` ids.
- The one exception to English is functional: `KittyTerminalDriver`'s ЙЦУКЕН keymap (`map=cmd+м …`).
- **A form field explains itself with a placeholder**, never a paragraph beside its button; the `*-state`
  spans are progress and verdict slots and start **empty**.
- **The same standard is demanded of the agents**, in `sub-agent-context.md`'s "How you write" section;
  a skill outranks the brief wherever the machine has one. `initialize_task` alone writes that brief, so an
  existing worktree keeps its wording; the relay `ship` names the description rule.
- **Prompt structure** (Anthropic guidance) in every prompt jagt writes: named XML sections (`<role>`,
  `<rules>`, `<output_format>`, `<examples>`), preamble forbidden explicitly, and "respond directly" rather
  than "do not think", which leaks `<thinking>` tags.
- **Never ask a CLI system prompt for JSON by wording alone**: the one place jagt takes JSON from a model is
  the headless assistant, where `--json-schema` constrains decoding.

## Every log line has the same grammar

The file log is ECS JSON: **nothing is interpolated into the message** — a constant event, every value its own
pair: `log.atWarn().setMessage("read failed").addKeyValue("url", url).addKeyValue("cause", failure).log()`.
Never `+` into a message, never `{}`. `logging.pattern.console` carries `%kvp`.

- **event**: 2 to 4 lowercase words, subject then state, past tense (`read failed`, `mcp probe skipped`,
  `worktree orphaned`, `process launched`), no punctuation, no values.
- **keys**: one word each, lowercase, from the shared vocabulary and never invented twice for one thing —
  `ref url api task alias project repo path file from to branch session window pid cmd exit tool method
  channel job port attempt limit tokens mcp servers status previous outcome said cause effect fix note`.
- `ref` is what was being read, `url` an address fetched or served, `said` the sentence a surface showed a
  human. One key means one type everywhere, and a number stays a number (`secrets=102`).
- `cause` is the failure itself (exception message, stderr, exit line; a throwable goes in `setCause(e)`),
  `effect` what jagt did instead, `fix` exactly one command or setting — never advice.
- **Never** in a value: prose, sentences, em dashes, a trailing period, a capital first word (identifiers keep
  their case), two facts, or anything that explains, reassures or advises.
- **ERROR** jagt could not do it · **WARN** it degraded, skipped or fell back · **INFO** a state change a
  human wants to see · **DEBUG** internal.
- INFO is for work nobody watched; a verb a human pressed logs nothing — `activity` reads that convention back.

## Code quality

- **The test is the litmus of the production code**: ~5+ objects of setup or high cognitive load means the
  smell is in the **production code** (`sob-ai:unit-testing` §5) — fix the code, never the fixture.
- Over the ceiling, group by composition and never inheritance, or use a registry of small units
  (`surface/mcp/McpTools`, `Move.actions()`).
- **Lombok carries the mechanical boilerplate and nothing else**: `@RequiredArgsConstructor`, `@Slf4j`,
  `@With` for a record's positional copy-withers (1.18.46 supports `@With` **and** `@Builder` on records
  under the Java 25 toolchain). By hand where the code is not mechanical:
  `OrchestratorPaths`' validating constructor, `TaskState.withStatus` stamping history,
  `TaskState.builder(project, worktree, status)`.
- The self-control loop, on every code + test change: changed tests through `sob-ai:unit-testing`, refactor
  the production code until they are light, re-run. Deliver only light, green **and** reviewed.

## Code review is mandatory after every code change, before committing

State the scope and a **level** every time; the last one typed is remembered. Stay at `medium` unless the
change is genuinely subtle — above it, eight to ten finder subagents plus a verifier each re-read the changed
files. Re-review if the fixes are non-trivial.
