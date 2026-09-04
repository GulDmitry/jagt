# Review rounds and unattended work

[← AGENTS.md](../../AGENTS.md)

## Code review is never fully automated

The auto-review poll (`AutoReviewScheduler` → `ReviewSweepService`) only **reads and drafts**: threads are
relayed, the agent fixes locally into `review_replies.md`, nothing is posted without a human `ship`, and the
round leaves the diff and the drafts for `ide <alias>`.

- Detection is deterministic — a cadence, a status, an open request; a new trigger must be too.
- `ReviewSweepService.brief` opens with three routes per thread: fix, change **nothing** and say why, or ask
  via `outcome=question`.
- A question **ends** the round (REVIEW_PENDING, `outcome=question`), never parks in CI_POLLING.
- `AgentSessions.relayIfChanged`: no re-brief on threads a session was told to hold.

## A round reports its outcome as a field, not as a turn of phrase

- `update_agent_status` takes `outcome` (`question` \| `no_changes` \| `progress`) and `reviewRequestUrl`, all
  three ending at REVIEW_PENDING. `flow/AgentReport`, its one parser, reads a marker jagt wrote
  (`AgentStatusReports.stated`).
- **`no_changes` is checked, not believed**: over uncommitted work it becomes a round **with** a diff
  (`WorktreeChanges`, one `git status` per report). NO_CHANGES highlights nothing.
- **The unit of a round is a THREAD, not a note** (`ReviewFacts.threads`): every note, oldest first with its
  author; the agent answers the **newest** one, and a thread whose newest note is its own is waiting on the
  reviewer. Relayed while unresolved — a resolved thread is closed and is never read again.
- The agent resolves at **ship** time over its own MCP (`ShipService.repliesStep`), and only threads whose
  code it changed; during the round it posts nothing, and a draft goes only where the note it answers is by
  an author matching `reviewReplyAuthors`.
- `TaskState.pipelineStatus` keeps the host's **own** wording and `flow/Pipeline` is its one parser →
  GREEN / RED / RUNNING / NONE / UNKNOWN — NONE no pipeline, UNKNOWN nobody having read one.
- **A red round relays the failing job's error lines** (`ReviewFacts.pipelineFailure`) as `<checks>`; read
  twice, a thread and a failure must read the same, or every poll re-briefs.

## The reply file is a review artifact, so its shape is prescribed in one place

- The shape is in the round brief (`ReviewSweepService.brief`), relayed **every** round: one block per
  thread — its link, the quoted newest note, `FIXED | NO CHANGE | QUESTION`, reply.
- **Drafted replies are a file, not state**: `TaskViews` stats `review_replies.md` for presence, never a
  count, and `ReviewDrafts.pending` wants it newer than `mrCreatedAt`.
- `replies [task]` (`command/ReviewRepliesReport`) prints every block off the **file**, never the badge,
  unrecognised shape included. The card's drafted-replies line is its only button
  (`GlobalCommand.aboutOneTask`).
- **A line typed at that report is SAID, not relayed** (`AgentSessions.say` → `nudgeTaskWindow`): a relay
  overwrites `task_context.md`.
- Who posts them decides when they are spent (`CodeReviewConfig.shipPostsEveryDraft`): under
  `postReviewReplies=false` or a partial `reviewReplyAuthors` filter nothing is, and the announcement stands
  until the human ends it.
- **jagt does not delete the file**: the agent is asked to, never the mechanism.
- **One review sweep per task at a time**: the guard is `ReviewSweepService`'s, ticks queueing in `Jobs`.

## Unattended work

- **An open request is what the poller watches, never a status** (`AutoReviewCadence.polls`): a request
  exists and the task is alive (DONE alone ends it).
- Polling's window is per round, from `mrCreatedAt`, restamped on every **entry** into CI_POLLING and kept
  by a repeat, `requestOpenedAt` the fallback.
- `AutoReviewCadence` is the **whole** policy: enabled, the interval ramp, `watch(task, now)` →
  `task/AutoReviewWatch` (the next-poll stamp, window elapsed, off, or nothing); `AutoReviewScheduler.decide`
  translates the same watch.
- Whether polling runs at all is the **install's** property, said once above the grid (`Board.autoReview` →
  `cadence.summary()`), never per card; a card's countdown is an absolute stamp (`core/format.js`).
- **Unattended work is a declared kind**: `job/Job` (id, one line for the human, an interval or `null` for
  once at startup, `run()`) and `job/Jobs`, the **one** ticker — one thread per run, never overlapping, a
  throw booked against that job.
- `command/ActivityReport` tails `logging.file.name` (ECS JSON) for entries carrying a `task` key, newest
  first — jagt's unattended work from its own log.
- **One run, one log**: `surface/ui/LogFileReset` empties the file and deletes the archives beside it before
  the appender opens; nothing gzipped is read.
