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

## Run the Master session against real work before anyone turns it on (idea)

It stays off until driven by hand against live tasks: what it judged, where its verdict and yours
differed, what it cost per wake. [`docs/roadmap.md`](docs/roadmap.md) has the shape; the finished-task record
is what the comparison is read from.

What to decide first: what a trial run may touch — reading and writing a file only, no verb issued.

## The closed loop: routing an item to a project, and events with names (concept, experimental)

Built: `TrackerWorkflow` per install, `IntakeJob` opening a task on the stage it names, `IntakeRouting`
placing it, and `TicketCloseJob` closing one on the stage that says the work landed, under
`ActionOrigin.TRACKER`.

Missing: named events, and routing beyond `Notification.Topic`'s four buckets.

What to decide first: where an event is declared so the compiler checks it, and whether routing is per channel,
per event or both.

## A rule the install outgrew is still taught (concept)

Built: `memory/routing.md`, written where labels placed nothing and read before every routing, one rule per
key, oldest dropped past sixty.

Missing: nothing notices a rule the world contradicts. You launching `do ABC-1 sc` by hand against what a rule
says is that contradiction, and a fact jagt holds. Dropping the oldest is not merging either: two rules saying
nearly the same thing survive until the ceiling evicts the wrong one.

What to decide first: whether a contradicted rule is deleted or kept with the date it stopped being true, since
only the second can say why a task went where it went last quarter.

## The Master checks the worker understood the brief (idea)

One read of `plan.md` against the brief before any code, keeping nothing: the verdict goes to the artifacts,
the context is dropped.

What to decide first: a new gate, or PLAN_PENDING judged by the Master rather than by you.
