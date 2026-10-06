# review Specification

## Purpose

How a review round is answered, reported and drafted into replies, and how an existing request is taken over.

## Requirements

### Requirement: The brief routes each thread
`ReviewSweepService.brief` SHALL route each thread: fix, change nothing and say why — beyond the ticket is a task of its own —
or ask via `outcome=question`. A question SHALL end the round (REVIEW_PENDING, `outcome=question`), never park in CI_POLLING.

#### Scenario: The comment is right
- **WHEN** a comment is right
- **THEN** the agent fixes it, uncommitted, never pushing on its own

#### Scenario: The comment is wrong
- **WHEN** a comment is wrong
- **THEN** the agent changes nothing and replies with the one technical reason

#### Scenario: Unclear, or a design decision
- **WHEN** a comment is unclear or forces a design decision
- **THEN** the agent asks: NEEDS INPUT on the board

#### Scenario: Contradicts the code
- **WHEN** a comment contradicts what the code enforces
- **THEN** the agent asks before the code picks a side

#### Scenario: Already handled
- **WHEN** a comment was already handled
- **THEN** the agent says so; nothing highlighted, no ship advised

### Requirement: No re-brief on held threads
`AgentSessions.relayIfChanged` SHALL NOT re-brief a session on threads it was told to hold.

#### Scenario: Held thread
- **WHEN** a poll reads a held thread
- **THEN** no re-brief

### Requirement: The outcome is a field
`update_agent_status` SHALL take `outcome` (`question` | `no_changes` | `progress`) and `reviewRequestUrl`, all three ending
at REVIEW_PENDING. `flow/AgentReport`, its one parser, SHALL read a marker jagt wrote (`AgentStatusReports.stated`).

#### Scenario: Clean round, nobody approved
- **WHEN** a round comes back clean, unapproved
- **THEN** REVIEWED, nothing asked of you

#### Scenario: An approval arrives
- **WHEN** an approval arrives
- **THEN** you are tapped, the one time: it is yours to deploy

### Requirement: no_changes is checked
Over uncommitted work `no_changes` SHALL become a round with a diff (`WorktreeChanges`, one `git status` per report).
NO_CHANGES SHALL highlight nothing.

#### Scenario: Claimed no change, files changed
- **WHEN** the agent says it changed nothing but files changed
- **THEN** not believed: the round has a diff

### Requirement: The unit of a round is a thread
`ReviewFacts.threads` SHALL carry every note, oldest first with its author; the agent answers the newest one, and a thread whose
newest note is its own waits on the reviewer. Threads SHALL be relayed while unresolved; a resolved one is never read again.

#### Scenario: GitHub review in the body
- **WHEN** a GitHub review is written in the body, not a thread
- **THEN** relayed all the same

### Requirement: Threads are resolved at ship
The agent SHALL resolve at ship time over its own MCP (`ShipService.repliesStep`), only threads whose code it changed. During the
round it SHALL post nothing, and a draft SHALL go only where the answered note's author matches `reviewReplyAuthors`.

#### Scenario: Replies are posted
- **WHEN** you `ship`
- **THEN** replies are posted only now; a fixed thread is resolved, a disputed one left for the reviewer

### Requirement: Pipeline status keeps the host's wording
`TaskState.pipelineStatus` SHALL keep the host's own wording, `flow/Pipeline` its one parser → GREEN, RED, RUNNING, NONE (no
pipeline), UNKNOWN (nobody read one). A round reading no listing SHALL write nothing, the last word standing flagged.

#### Scenario: No checks dot while the host shows a failed run
- **WHEN** no checks dot shows while the host shows a failed run
- **THEN** nobody read one yet; `none` means the host listed none

### Requirement: A red round stops the task
`FlowRules.readRed` SHALL stop the task and relay the failing job's lines as `<checks>`, a clue; read twice they MUST read the
same, or every poll re-briefs.

#### Scenario: Pipeline goes red
- **WHEN** the pipeline goes red
- **THEN** `checks failed` on the card, one notification; the session finds and fixes the cause

#### Scenario: Red, no comments
- **WHEN** checks are red, no comments
- **THEN** the agent fixes the build and hands back

#### Scenario: Red outside the code
- **WHEN** the red is not in the code, or nothing here reads it
- **THEN** the session changes nothing and asks you, naming the job

### Requirement: The reply shape is prescribed once
The round brief (`ReviewSweepService.brief`), relayed every round, SHALL prescribe one block per thread: its link, the quoted
newest note, `FIXED | NO CHANGE | QUESTION`, reply.

#### Scenario: Replies too long
- **WHEN** replies run long, or an essay
- **THEN** the agent broke its brief; a re-`sweep` re-briefs

### Requirement: Drafted replies are a file
`TaskViews` SHALL stat `review_replies.md` for presence, never a count; `ReviewDrafts.pending` SHALL want it newer than
`mrCreatedAt`. jagt SHALL never delete the file; the agent is asked to.

#### Scenario: Stale reply file
- **WHEN** the file predates `mrCreatedAt`
- **THEN** nothing is pending

### Requirement: The replies report reads the file
`replies [task]` (`command/ReviewRepliesReport`) SHALL print every block off the file, never the badge, unrecognised shape
included. The card's drafted-replies line SHALL be its only button (`GlobalCommand.aboutOneTask`).

#### Scenario: What will be posted
- **WHEN** you ask what will be posted
- **THEN** `replies <task>` or the card's drafted-replies line shows every draft

### Requirement: A line typed at the report is said
A line typed at the replies report SHALL be said (`AgentSessions.say` → `nudgeTaskWindow`), never relayed: a relay overwrites
`task_context.md`.

#### Scenario: A drafted reply is wrong
- **WHEN** you say so under the open `replies` report
- **THEN** the session answers into the same report

### Requirement: Who posts decides when drafts are spent
`CodeReviewConfig.shipPostsEveryDraft` SHALL decide: under `postReviewReplies=false` or a partial `reviewReplyAuthors` filter no
draft is spent, and the announcement stands until the human ends it.

#### Scenario: Posting off
- **WHEN** you `ship` under `postReviewReplies=false`
- **THEN** the announcement stands

### Requirement: One sweep per task
`ReviewSweepService` SHALL guard one review sweep per task at a time, ticks queueing in `Jobs`.

#### Scenario: Tick during a sweep
- **WHEN** a tick meets a running sweep
- **THEN** it queues

### Requirement: Resume takes over a request
`resume <url>` SHALL make the request's source branch the task and its target the base, leaving conflicts for the session.

#### Scenario: Other branch convention
- **WHEN** its branch follows another convention (`feature/x`)
- **THEN** taken over as-is

#### Scenario: Target deleted
- **WHEN** its target branch was deleted since
- **THEN** it works; only the next `ship` needs one

#### Scenario: Branch already a task
- **WHEN** its branch belongs to a task
- **THEN** refused: two tasks cannot share a branch

#### Scenario: No working MCP server
- **WHEN** no MCP server for that host works
- **THEN** refused as unread, never missing, naming the servers down

#### Scenario: No such request
- **WHEN** the host answers there is no such request
- **THEN** refused in those words: the one case "does not exist" is said

### Requirement: GitHub approval without a required review
A GitHub request with no review required SHALL count as approved once someone approves.

#### Scenario: Approved, not required
- **WHEN** someone approves where no review is required
- **THEN** approved
