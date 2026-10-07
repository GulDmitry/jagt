# jagt — TODO

## The jar runs only inside a clone of jagt (open)

`OrchestratorPaths.findRoot` walks up for `jagt.yml.dist` or `mcp_client.js`, and `AbstractAgentRuntime`
symlinks that bridge from disk, so a released jar starts nowhere else. Plan: both as classpath resources, root
falling back to the launch directory, written there on first run.

Decide first: what `root` means once nothing marks it, since worktrees are cut beside it.

## jagt reads the code host and the tracker itself (concept, someday)

Before holding a token, jagt promises: where it is read from, what may act with it, what the board
needs first — loopback, no auth, able to deploy. [`docs/rules/seams.md`](docs/rules/seams.md).

## One task's work split into reviewable requests (idea)

A large ticket lands as one unreviewable request; a splitter would cut it into 300–400 changed-line requests.

Decide first: where a cut may fall (only a commit boundary stays buildable), how each targets the
previous, and the fate of comments a later cut superseded.

## A project's memory lives in its own agent file (open)

Measured, no memory layer ([§8](docs/research/memory-layer.md#8-measured-on-our-own-tasks-2026-10-06)): Claude's auto
memory, loaded in every worktree, rotted into ticket notes, skill duplicates and "never commit" rules.

- Triage it: a still-true project fact moves to its agent file, the rest goes.
- A worker that hit a gap adds a line to the project's agent file in its branch, reviewed in the MR.

Decide first: the guard at hand-back, beside `TaskNotes.owed` — how many lines, which justification field
the report carries.

A memory layer reopens on one correction recurring in 3+ tasks, or rediscovery past 10% of tool calls.

## A card flag for the tasks that matter (idea)

An inbox flag, top-right, for the few tasks you must not lose among alike cards.

Decide first: `card-top` ends in the attention badge, so a flag says what it replaces; no colour is free
(`--danger` broken, `--you` your move). Likely a shape, sorted first, not tinted.

## The Master on real work, one day from a pipeline (idea)

Built: `masterEval` on known-verdict rounds, the `master` report counting where its verdicts and yours
differed. Left: `judge` on live work, that count read back. A pipeline run needs an agent CLI, a trusted root,
tokens and minutes; a varying verdict is an unreproducible red build.

Decide first: what a trial run may touch — a file read and written, no verb issued.

## Events with names, and routing past four buckets (concept)

Tracker loop built; no event is declared where the compiler sees it; `Notification.Topic`'s four
buckets are all the routing.

Decide first: where an event is declared, and routing per channel, per event or both.

## Two rules meaning the same thing both survive (concept)

Built: `memory/routing.md` counts what each rule placed; past the ceiling the least used goes.

Missing: a merge. The prompt asks for an existing phrase verbatim; a rewording still becomes a second
rule with its own count, both shown to the router while they fit.

Decide first: the router's answer or a file pass — only a pass sees two rules nobody asked about
together.

## The Master checks the worker understood the brief (idea)

One read of `plan.md` against the brief before any code: verdict to the artifacts, context dropped.

Decide first: a new gate, or PLAN_PENDING judged by the Master instead of you.

## The rest of the token audit (open)

Left by [0009](docs/decisions/0009-what-jagt-can-read-it-reads-once-and-quotes.md), measured first: MCP result bytes
in `ToolHandler`; the worker heartbeat, now hooks feed `SessionProbe`; review sections only in a review round.
