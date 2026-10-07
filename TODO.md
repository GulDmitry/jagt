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

## Triage the auto memory left in project repositories (open)

Workers neither read nor write it now, and a line they add to the agent file is capped and named. Left: the files
already there — a still-true project fact moves to its agent file, the rest goes.

A memory layer reopens on one correction recurring in 3+ tasks, or rediscovery past 10% of tool calls.

## A card flag for the tasks that matter (idea)

An inbox flag, top-right, for the few tasks you must not lose among alike cards.

Decide first: `card-top` ends in the attention badge, so a flag says what it replaces; no colour is free
(`--danger` broken, `--you` your move). Likely a shape, sorted first, not tinted.

## Read the Master's record back (open)

After ~10 finished tasks under the default `judge`, read the `master` report: where its verdicts and yours
differed reopens [0018](docs/decisions/0018-the-master-judges-by-default.md) or the brief.

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
