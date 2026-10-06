# jagt — TODO

## The jar runs only inside a clone of jagt (open)

`OrchestratorPaths.findRoot` walks up for `jagt.yml.dist` or `mcp_client.js`, and `AbstractAgentRuntime`
symlinks that bridge from disk: repository files, so a released jar starts nowhere else. Plan: ship both as
classpath resources, root falling back to the launch directory, written there on first run.

What to decide first: what `root` means once nothing marks it, since worktrees are cut beside it.

## An orchestrator that reads the code host and the tracker itself (concept, someday)

What jagt must promise before holding a token: where it is read from, what may act with it, what the board
needs first — loopback, no auth, able to deploy. [`docs/rules/seams.md`](docs/rules/seams.md).

## Split one task's work into readable review requests (idea)

A large ticket lands as one request nobody can review; a splitter would cut it into requests of 300–400 changed
lines each.

What to decide first: where a cut may fall (only a commit boundary stays buildable), how each targets the
previous, and what happens to comments a later cut superseded.

## A project's memory lives in the project's own agent file (open)

Measured, no memory layer ([§8](docs/research/memory-layer.md#8-measured-on-our-own-tasks-2026-10-06)): Claude's auto
memory, loaded in every worktree, rotted into ticket notes, skill duplicates and "never commit" rules.

- Triage the accumulated auto memory: a still-true fact about a project goes to its agent file, the rest is deleted.
- Workers stop writing auto memory: `autoMemoryEnabled: false` in the worktree's generated settings.
- A worker that hit a gap adds a line to the project's `AGENTS.md`/`CLAUDE.md` in its own branch, reviewed in the MR.

What to decide first: the guard at hand-back, beside `TaskNotes.owed` — how many lines, and which justification
field the report must carry.

What reopens a memory layer: one correction recurring in 3+ tasks after that, or rediscovery past 10% of tool calls.

## A flag for the tasks that matter, on the card (idea)

An inbox flag, top-right: the few tasks you must not lose, on a board where every card reads alike.

What to decide first: `card-top` already ends in the attention badge, so a flag there says what it replaces —
and no colour is free, `--danger` meaning broken and `--you` your move. Likely a shape, sorted first not tinted.

## Run the Master against real work, and one day from a pipeline (idea)

Built: `masterEval` against rounds of known verdict, the `master` report counting where its verdicts and yours
differed. Left: `judge` on live work, that count read back. A pipeline run wants an agent CLI, a trusted root,
tokens and minutes, and a varying verdict is a red build nobody reproduces.

What to decide first: what a trial run may touch — a file read and written, no verb issued.

## Events with names, and routing past four buckets (concept)

Both ends of the tracker loop are built; naming is not: no event is declared where the compiler sees it, and
`Notification.Topic`'s four buckets are the whole routing.

What to decide first: where an event is declared, and routing per channel, per event or both.

## Two rules meaning the same thing both survive (concept)

Built: `memory/routing.md` carries what each rule has placed, and past the ceiling the least used goes.

Missing: a merge. The prompt asking for an existing phrase verbatim is the only defence; a rewording becomes a
second rule with its own count, both shown to the router while they fit.

What to decide first: the router's answer or a pass over the file — only the second sees two rules nobody asked
about together.

## The Master checks the worker understood the brief (idea)

One read of `plan.md` against the brief before any code: the verdict goes to the artifacts, the context dropped.

What to decide first: a new gate, or PLAN_PENDING judged by the Master rather than by you.

## The rest of the token audit (open)

Left by [0009](docs/decisions/0009-what-jagt-can-read-it-reads-once-and-quotes.md), measured first: MCP result bytes
in `ToolHandler`; the worker heartbeat, now hooks feed `SessionProbe`; review sections only in a review round.
