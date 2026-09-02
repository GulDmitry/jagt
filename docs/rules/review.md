# Review rounds and unattended work

[← AGENTS.md](../../AGENTS.md)

## Code review is never fully automated

The auto-review poll (`AutoReviewScheduler` → `ReviewSweepService`) only **reads and drafts**: comments are
relayed, the agent fixes locally into `review_replies.md`, and nothing is posted without an explicit human
`ship`. Each round leaves the diff and the drafts for `ide <alias>`.

- Detection is deterministic — a cadence, a status, an open request (`AutoReviewCadence` →
  `AutoReviewScheduler`); a new trigger must be too.
- `ReviewSweepService.brief` opens with three routes per comment: fix, change **nothing** and say why, or ask
  via `outcome=question`.
- A question **ends** the round (REVIEW_PENDING, `outcome=question`), never parks in CI_POLLING.
- `AgentSessions.relayIfChanged`: no re-brief on comments a session was told to hold.

## A round reports its outcome as a field, not as a turn of phrase

- `update_agent_status` takes `outcome` (`question` \| `no_changes` \| `progress`) and `reviewRequestUrl`, all
  three ending at REVIEW_PENDING. `flow/AgentReport`, the one parser of that vocabulary, reads a marker jagt
  wrote (`AgentStatusReports.stated`).
- **`no_changes` is checked, not believed**: over uncommitted work it becomes a round **with** a diff
  (`WorktreeChanges`, one `git status` per report). NO_CHANGES highlights nothing.
- **A reply does not resolve a thread**: every unresolved one is relayed again (`resolvable && !resolved`).
- The agent resolves at **ship** time over its own MCP (`ShipService.repliesStep`), and only threads whose
  code it changed; during the round it posts nothing.
- Checks are read where the comments are: `TaskState.pipelineStatus` keeps the host's **own** wording and
  `flow/Pipeline` is its one parser → GREEN / RED / RUNNING / NONE / UNKNOWN, NONE no pipeline and UNKNOWN
  nobody having read one.

## The reply file is a review artifact, so its shape is prescribed in one place

- The shape is in the round brief (`ReviewSweepService.brief`), relayed **every** round: one block per
  comment — thread, quoted line, `FIXED | NO CHANGE | QUESTION`, reply.
- **Drafted replies are a file, not state**: `TaskViews` stats `review_replies.md` for presence, never a
  count.
- `replies [task]` (`command/ReviewRepliesReport`) prints every comment, its verdict and the reply to be
  posted — off the **file**, never the badge, unrecognised shape included. The card's drafted-replies line is
  its only button (`GlobalCommand.aboutOneTask`).
- **A line typed at that report is SAID, not relayed** (`AgentSessions.say` → `nudgeTaskWindow`):
  a relay overwrites `task_context.md`. It replaces the trip through `focus`.
- **Drafts belong to their round**: `ReviewDrafts.pending` is the file present **and** newer than
  `mrCreatedAt`.
- Who posts them decides when they are spent (`CodeReviewConfig.shipPostsEveryDraft`): under
  `postReviewReplies=false` or a partial `reviewReplyAuthors` filter nothing is spent, and the announcement
  stands until the human ends it.
- **jagt does not delete the file**: the agent is asked to, a courtesy and never the mechanism.
- **One review sweep per task at a time**: the guard is `ReviewSweepService`'s, ticks queueing in `Jobs`.

## Unattended work

- **An open request is what the poller watches, never a status**: `AutoReviewCadence.polls` asks whether a
  request exists and whether the task is alive (DONE alone ends it).
- The window bounds polling: per round from `mrCreatedAt`, restamped on every **entry** into CI_POLLING and
  kept by a repeat, `requestOpenedAt` the fallback.
- `AutoReviewCadence` is the **whole** policy: enabled, the interval ramp, `watch(task, now)` →
  `task/AutoReviewWatch` (watching plus the absolute next-poll stamp, window elapsed, off for this task, or
  nothing). `AutoReviewScheduler.decide` translates the same watch.
- Whether polling runs at all is the **install's** property, said once above the grid (`Board.autoReview` →
  `cadence.summary()`), never per card. A card's countdown is an absolute stamp (`core/format.js`).
- **Unattended work is a declared kind**: `job/Job` (id, one line for the human, an interval or `null` for
  once at startup, `run()`) and `job/Jobs`, the **one** ticker — one thread per run, never overlapping itself,
  a throwing run booked against that job alone.
- `command/ActivityReport` tails `logging.file.name` (ECS JSON) for entries carrying a `task` key, newest
  first — jagt's unattended work, read back from its own log.
- **One run, one log**: `surface/ui/LogFileReset` empties the file and deletes the archives beside it before
  the appender opens; nothing gzipped is read back.
