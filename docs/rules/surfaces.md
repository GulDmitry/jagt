# Control surfaces

[← AGENTS.md](../../AGENTS.md)

## Control surfaces

**One** front-end over the core: the board, served from the jar on loopback. A full-screen console shipped
beside it until 2026-08-26 and was removed — it duplicated every verb and every projection, and a second
surface is a second place for a capability to go missing from.

**Adding a surface would have to add no second answer to any question the board already answers.** That is the
bar, and it is why there is one.

### One projection answers "what is this task and what can I do with it"

`flow/Move` + `flow/TaskView`, built by `service/TaskViews` and rendered by `/api/tasks`. `Move.shippable` is
also what `ShipService.requireShippable` calls — the card used to advise independently of the gate, which is
exactly how they drifted apart.

`TaskViews.snapshot()` reads the configuration **once** per render and hands back the tasks with the policy
that explains them: two reads could disagree inside one frame.

### Nothing is added to a surface without saying what it replaces

A board is read at a glance or it is not read at all, and every mark spends the same budget: one glance.

- **No two controls for one question.** "How does this work" has a button already; a colour legend is a
  *section of it*, not a second button beside it. The same goes for a report, a filter and a form.
- **A fact goes on the thing it is about.** That a deploy already landed belongs on the Deploy button — it is
  what the fact changes — not on a row of chips a human has to scan and match up.
- **A row that already carries four things does not want a fifth, and a card must not grow taller to hold
  one.** Height and cognitive load are the same budget spent twice. The card's meta row is where this broke
  once (2026-08-26): status, project, request, approval, checks, poll — six marks, four of them facts about
  the same review request. Two now, one fact each: the request link, green and ticked once approved, and a dot
  beside it for the checks. Both on the link cost more than the mark it saved — red outranked the approval
  there, so a build that broke on an already-approved request read as any other failure, and those two want
  opposite moves (2026-08-26).
- **What is merely coming is not news.** The poll countdown moved into that tooltip; a poll that has STOPPED
  keeps its own element, because it hands the move back to a human and nothing else on the card says so.
- **A word every card wears is a word nobody reads.** The project key is rendered only where an install has
  more than one, or a task spans several.
- **A colour means ONE thing, board-wide, and colour is the cheapest mark to overspend.** Which colour means
  what, what each mark IS, and the legend that has to carry it: [`design.md`](design.md). This file stays the
  cost of adding one.

### Parity is an invariant, not an aspiration

A capability the board cannot express is a bug.

- Per-task verbs come from `Move.actions()`, so a new action appears by declaring it — **grouped** there too
  (`TaskAction.Group`: FLOW moves the task on, and closing it counts; TOOL only looks at it or restarts the
  agent). `Move` **sorts** by that group rather than trusting the order somebody appended in, so a new verb
  lands on the right side of the card by declaring its group and nothing else.
- The board renders one row per group and reads which groups exist **off the wire** — a page that knew the
  names would be a second answer.
- The grammar's text lives in `command/CommandReference`, so no caller renders its own version of a hint.
- **A hint is `data-tip`, never `title`**: one node placed on hover (`showTip`), because `title` waits and a
  push rebuilds the element it waited on.
- Reports open in a `<dialog>` over the board, never a new page. **Every dialog closes three ways**: Escape,
  its own button, and the dimmed area around it — the click a human makes first. The backdrop close is guarded
  by where the press **started**, so dragging a selection out of a report does not dismiss what is being read.

The **legend** is a section of the `help` report (`static/ui/legend.js`), never a control beside it: it says
what a colour, a ring and a pulsing dot mean by rendering the page's own elements rather than naming their
colours.

**Stopping the backend is not a verb.** It belongs to whoever owns the process (Ctrl-C / kill), not to a
browser button, and nothing is lost since agents live in tmux. A shutdown endpoint was built and removed — do
not add one back.

### "What commands exist" has exactly two answers, and both are declarations

| the verb | is | executed by |
|---|---|---|
| one a task owns | a `flow/TaskAction` row, gated by `Move` | `CommandService` |
| one no task owns | a `command/GlobalCommand` bean (`command/*`, collected by `GlobalCommands`) — id, hint, usage, whether its answer is a report, whether that report is about one task | itself |

`CommandReference` **renders both** — `help`'s text and the palette's verb list — so a hint is written once.
`GET /api/commands/{id}` serves any report, so declaring another one needs no endpoint and no button in the
page (the board *builds* its report buttons from that list).

This is what parity failed on before (2026-08-19): the per-task verbs always had this shape, while
`do`/`resume`/`stats`/`activity`/`help` were hand-written in six places each.

Three deliberate limits: that endpoint refuses anything that is not a report (a GET must not be able to start
a task); a report **about one task** (`aboutOneTask`) gets no button in the bar, because the card that has
something to show is where a human presses it and a bar button would answer for every task at once — it stays
typeable; and tier 2 stays narrower on purpose — a prose request cannot ask for a dialog, so
`NaturalLanguageDispatch` names the two launches itself and offers no report.

### Two-tier dispatch

**Tier 1** is the grammar (a palette line that parses, or a board button) and it stays LLM-free.

**Tier 2** is `service/NaturalLanguageDispatch`: free text from the board's ⌘K palette →
`POST /api/interpret` — goes to a model that only **proposes** one grammar command. The dispatcher
validates that the task exists and the verb is real, then executes through `CommandService`, so **tier 2 can
never do more than a button**.

The call is deliberately stripped (`--strict-mcp-config --mcp-config '{"mcpServers":{}}'`, no
`--setting-sources`): text→command needs no tools, and a loaded MCP server would be paid for in context.

It answers with the interpretation **first** ("understood as `ship a1` — …"), and a single unknown word is a
typo, not a request — it never reaches the model.

### A renamed verb keeps its old spelling, and advertises only the new one

`sweep`, typed as `review` since 2026-08-18. **One map owns it** — `TaskAction.RENAMED`, read through
`byRetiredVerb` — and everywhere a human types has to consult it: the palette
(`CommandReference.Verb.aliases`, which the page matches and offers nowhere) and a tier-2 proposal that echoed
the word.

Two things it deliberately is not: `byId` stays **strict**, because that one answers a URL segment and a
retired verb is not a wire id; and the lookup resolves retired spellings **only**, never a current id — the
grammar's verb set is the switch, so `diff …` keeps reaching the model as free text.

`CommandReference` names just the current verb: two spellings in `help` are two answers to one question.

### How an action is executed

`service/CommandService` validates against `Move` first, so a stale board tab is refused with a sentence, not
with a git error three layers down. `service/TaskLauncher` is how a task is started. The controller parses
JSON and the palette parses a typed line; **neither owns rules.**

The sentence stays the whole answer for a human. A refusal a caller must **act** on also carries a
`flow/Refusal.Code`, and that enum grows **only** when something branches on the new value — a reason nobody
handles differently keeps throwing plain.

### There is no tools facade any more

Do not bring one back. `OrchestratorTools` grew to 871 lines and eleven collaborators, and every attempt to
thin it *added* one, because a delegating aggregate keeps what it does not shed. It was dissolved
(2026-08-14).

Each MCP tool group declares its own tools (`surface/mcp/McpTools` + `surface/mcp/McpToolRegistry`,
implementations under `surface/mcp/tools`). Every other caller takes the small service it actually uses:
`AgentSessions` (tmux window, focus, kill, relay), `TaskProvisioning` + `WorktreeSetup` + `SubAgentBriefing`
(creation), `AgentStatusReports`, `IdeLauncher`, `DeployService` (the only shared-branch writes),
`TaskRetirement`, `TaskResume`. Per-task verbs are a class each under `capability/`, reached through the flow
engine. `surface/mcp/CallerScope` owns the X-Working-Directory rule for all of them.

### No limit on concurrent tasks

A decision, not an omission. A cap (`agent.maxConcurrentTasks` + `TaskAdmission`) was built and then **removed
on the owner's instruction**: jagt runs on other people's machines, one of which has 100 GB of RAM, so a
number picked here is wrong for almost everyone, and refusing a `do` on that basis is jagt deciding something
it cannot know. Whoever wants a bound has the machine's own tools for it.

Do not reintroduce a cap, a queue, or a "slots" indicator.

### The board listens on loopback

`server.address: 127.0.0.1`, because it asks for no password and can deploy, close a task and start an agent.
Widening it is a config line the human writes themselves.

### The board is two rings too

Vanilla HTML/CSS/JS under `src/main/resources/static` — **no build step, no CDN, no external asset of any
kind** (it must work with the machine offline and stay inside the one jar).

Native ES modules in two rings, the same rule as the backend's: `core/` answers a question without owning a
node on the page, `ui/` owns the nodes it renders, `app.js` only wires them — and a module never reaches for
another's element (it takes a callback at wiring time).

A card carries `data-action`, never a closure: `ui/render` holds the one delegated listener on the grid, so a
card rebuilt under the pointer cannot act for the task it used to describe.

`ARCHITECTURE.md` has the file-by-file map.

### The board does not poll

`StateService.onChange` is the one event: `TaskEventStream` forwards it as SSE. The listener runs on whichever
thread served the agent's MCP call, so it hands over a notification and nothing else.

The SSE event carries **no payload** on purpose: a payload would be a second serialization that could disagree
with `/api/tasks`. The periodic tick survives only for the relative "ACTIVE" clock.

### A desktop banner clicks through to the task it is about

`DesktopNotifier` → `UserNotifier.notify(…, link)`. A banner that only **names** a task leaves the human to
find it, which on macOS means finding the browser tab first.

The link is the board narrowed to that task by the filter the page already has — not a second way to address a
card — and it is omitted when the banner names no task, since a click onto a page about nothing is worse than
one that does nothing.

It exists at all only because macOS banners cannot carry an action without `terminal-notifier` (`-open`),
which `MacNotifier` already prefers for its own reasons. osascript and `notify-send` both drop the link, and
**no caller may depend on it.**
