# jagt — TODO

## The jar runs only inside a clone of jagt (open)

`OrchestratorPaths.findRoot` walks up for `jagt.yml.dist` or `mcp_client.js`, and `AbstractAgentRuntime`
symlinks that bridge from disk — both repository files, so a released jar starts nowhere else. The plan: ship
both as classpath resources, fall back to the launch directory as root, and write them there on first run.

What to decide first: what `root` means once nothing marks it, since worktrees are cut beside it.

## An orchestrator that reads the code host and the tracker itself (concept, someday)

What jagt must promise before it may hold a token: where it is read from, what may act with it, and what the
board needs first — loopback, no auth, able to deploy. [`docs/rules/seams.md`](docs/rules/seams.md).

## Split one task's work into review requests a human can actually read (idea)

A large ticket lands as one request, and nobody reviews a request nobody can read. A splitter would send the
same work out as several, cut at a configured size — 300–400 changed lines each.

What to decide first: where a cut may fall (only a commit boundary leaves each request buildable), how they are
chained so the second targets the first, and what happens to comments a later cut has superseded.

## A flag for the tasks that matter, on the card (idea)

An inbox flag, top-right: the few tasks you must not lose sit in a board where every card reads alike.

What to decide first: `card-top` already ends in the attention badge, so a flag there says what it replaces —
and no colour is free, `--danger` meaning broken and `--you` your move. Likely a shape, sorted first not tinted.

## Run the Master against real work, and one day from a pipeline (idea)

`masterEval` reads the shipped brief against rounds whose verdict is known, by hand. Live work it does not
answer: what the Master judged, where its verdict and yours differed, what it cost per wake. A pipeline could
run it, but wants an agent CLI, a trusted root, tokens and minutes — and a verdict that varies is a red build
nobody can reproduce.

What to decide first: what a trial run may touch — a file read and written, no verb issued.

## Events with names, and routing past four buckets (concept)

Both ends of the tracker loop are built. What is missing is naming: an event is declared nowhere the
compiler can see it, and `Notification.Topic`'s four buckets are all the routing there is.

What to decide first: where an event is declared, and whether routing is per channel, per event or both.

## Two rules meaning the same thing both survive (concept)

Built: `memory/routing.md` carries what each rule has placed, and past the ceiling the least used goes rather
than the oldest.

Missing: nothing merges two rules that mean the same thing. The prompt asks for an existing phrase back
character for character, and that is the whole defence — a rewording becomes a second rule with its own count,
and both are shown to the router for as long as they fit.

What to decide first: whether merging is the router's answer or a pass over the file, since only the second can
see two rules nobody asked about together.

## The Master checks the worker understood the brief (idea)

One read of `plan.md` against the brief before any code, keeping nothing: the verdict goes to the artifacts,
the context is dropped.

What to decide first: a new gate, or PLAN_PENDING judged by the Master rather than by you.
