# Control surfaces

[← AGENTS.md](../../AGENTS.md)

**One** front-end over the core: the board, from the jar on loopback. One projection: `flow/Move` +
`flow/TaskView`, built by `service/TaskViews`, rendered by `/api/tasks`. `Move.shippable` is what
`ShipService.requireShippable` calls; `TaskViews.snapshot()` reads the configuration **once** per render.
A second surface must add no second answer to a question the board answers — the console that did was removed.

## Nothing is added to a surface without saying what it replaces

- The colour legend is a *section* of the `help` report (`static/ui/legend.js`), never a control beside it.
  Colours are [`design.md`](design.md)'s.
- **A control the board lacks is not a preference, it is the bug**: the launch row asks for the branch
  strategy, and that sentence, the picker and the MCP schema all read `task/BranchStrategy`. An untouched
  picker sends nothing.
- **A word every card wears is a word nobody reads**: the project key renders only where an install has more
  than one project, or a task spans several.

## Parity is an invariant, not an aspiration

- Per-task verbs come from `Move.actions()`, grouped there (`TaskAction.Group`: FLOW moves the task on, TOOL
  only looks); the board renders a row per group, reading the groups **off the wire**.
- A hint's text lives in `command/CommandReference`, so no caller renders its own.
- A report opens in a `<dialog>` over the board, never a new page, and **every dialog closes three ways**:
  Escape, its button, the dimmed area.
- A report about ONE task carries a one-line box that types into that session (`POST /api/tasks/say`) — not a
  verb, and the only control there.
- Stopping the backend is **not a verb**; the shutdown endpoint once built was removed.

## "What commands exist" has exactly two answers, and both are declarations

A verb a task owns is a `flow/TaskAction` row gated by `Move`, run by `CommandService`; one no task owns is a
`command/GlobalCommand` bean (`command/*`, collected by `GlobalCommands`: id, hint, usage, report or not, about
one task or not) running itself. `CommandReference` renders both — `help`'s text and the palette's verb
list — and `GET /api/commands/{id}` serves any report, so another needs no endpoint; it refuses anything that
is not a report, since a GET must not start a task. A report **about one task** (`aboutOneTask`) gets no button
in the bar, and tier 2 offers none.

## Dispatch, verbs and execution

- **Tier 1 is the grammar** (a parsing palette line, or a board button) and stays LLM-free.
- **Tier 2** is `service/NaturalLanguageDispatch`: free text from the ⌘K palette → `POST /api/interpret` → a
  model that only **proposes** one grammar command, validated (task and verb exist) and executed through
  `CommandService`, so **it can never do more than a button**.
- That call is stripped (`--strict-mcp-config --mcp-config '{"mcpServers":{}}'`, no `--setting-sources`) and
  answers with the interpretation **first**.
- A renamed verb keeps its old spelling and advertises only the new (`sweep`, typed as `review`): **one map
  owns it**, `TaskAction.RENAMED` through `byRetiredVerb`, read by the palette
  (`CommandReference.Verb.aliases`) and by tier 2.
- `service/CommandService` validates against `Move` first, so a stale board tab is refused with a sentence and
  not a git error three layers down. `service/TaskLauncher` starts a task; controller and palette **own no
  rules**.
- A refusal's sentence is the whole answer; one a caller must **act** on carries a `flow/Refusal.Code`, which
  grows **only** when something branches on it.
- **No tools facade, ever**: each MCP tool group declares its own (`surface/mcp/McpTools` + `McpToolRegistry`,
  under `surface/mcp/tools`); `surface/mcp/CallerScope` owns the X-Working-Directory rule.

## The board itself

- It listens on loopback (`server.address: 127.0.0.1`): no password, yet it can deploy, close a task, start an
  agent.
- A card carries `data-action`, never a closure: `ui/render` holds the one delegated listener on the grid, so a
  card rebuilt under the pointer cannot act for its old task.
- It does not poll: `StateService.onChange` is the one event, `TaskEventStream` forwarding it as SSE with **no
  payload**, which a second serialization could contradict. The periodic tick survives for the "ACTIVE" clock.
- A desktop banner clicks through to its task (`DesktopNotifier` → `UserNotifier.notify(…, link)`), macOS-only
  via `terminal-notifier`'s `-open` (`MacNotifier` prefers it; osascript and `notify-send` drop it): **no
  caller may depend on it**.
