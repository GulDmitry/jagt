# review Specification

## Purpose

How a review round is answered, reported and drafted into replies, and how an existing request is taken over.

## Requirements

### Requirement: The brief routes each thread
`RoundBrief.of` SHALL route each thread: fix, change nothing and say why — beyond the ticket is a task of its own —
or ask via `outcome=question`. It SHALL send a question to REVIEW_PENDING, never CI_POLLING.

#### Scenario: The comment is right
- **WHEN** a comment is right
- **THEN** the agent fixes it, uncommitted, never pushing on its own

#### Scenario: The comment is wrong
- **WHEN** a comment is wrong
- **THEN** the agent changes nothing and replies with the one technical reason

#### Scenario: Unclear, a design decision, or against the code
- **WHEN** a comment is unclear, forces a design decision or contradicts what the code enforces
- **THEN** the agent asks, before the code picks a side: NEEDS INPUT on the board

#### Scenario: Already handled
- **WHEN** a comment was already handled
- **THEN** the agent says so; nothing highlighted, no ship advised

### Requirement: No re-brief on held threads
`TaskStops` and the session's open turn SHALL NOT re-brief a session on threads it was told to hold.

#### Scenario: Held thread
- **WHEN** a poll reads a held thread
- **THEN** no re-brief

### Requirement: The outcome is a field
`update_agent_status` SHALL take `outcome` (`question` | `no_changes` | `progress`) and `reviewRequestUrl`, the brief ending
all three at REVIEW_PENDING. What a report means SHALL be the task's `task/AgentReport`, never a marker read out of its
line; an outcome typed into the message is words. A `state.json` from before SHALL read it once from the line's marker.

#### Scenario: Clean round, nobody approved
- **WHEN** a round comes back clean, unapproved
- **THEN** REVIEWED, nothing asked of you

#### Scenario: An approval arrives
- **WHEN** an approval arrives
- **THEN** you are tapped, the one time: it is yours to deploy

#### Scenario: A marker typed into the message
- **WHEN** a session reports `outcome=question: which branch?` as its message and no `outcome`
- **THEN** the line reads as written, and nobody is asked

#### Scenario: A question from before the field
- **WHEN** jagt starts on a `state.json` whose line reads `awaiting: which database`
- **THEN** the card reads `NEEDS INPUT: which database`

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

#### Scenario: The agent answered last
- **WHEN** two polls read a thread whose newest note is by the account the host's tools act as
- **THEN** neither relays it, the task is not REVIEWED, and only a new note on it starts a round

### Requirement: Threads are resolved at ship
The agent SHALL resolve at ship time over its own MCP (`ShipService.repliesStep`), only threads whose code it changed. During the
round it SHALL post nothing, and a draft SHALL go only where the answered note's author matches `reviewReplyAuthors`.

#### Scenario: Replies are posted
- **WHEN** you `ship`
- **THEN** replies are posted only now; a fixed thread is resolved, a disputed one left for the reviewer

### Requirement: Pipeline status keeps the host's wording
`TaskState.pipelineStatus` SHALL keep the host's own wording, `flow/Pipeline` its one parser → GREEN, RED, RUNNING, NONE (no
pipeline), UNKNOWN (nobody read one). A round reading no listing SHALL write nothing, the last word standing flagged. The
third such round running since the `lift` SHALL stop the task, its evidence the reads' words for why; a listing counts anew.

#### Scenario: No checks dot while the host shows a failed run
- **WHEN** no checks dot shows while the host shows a failed run
- **THEN** nobody read one yet; `none` means the host listed none

#### Scenario: Checks nobody can list
- **WHEN** a third poll running cannot list ABC-42's pipelines, `list_pipelines: 403 Forbidden`
- **THEN** jagt stops it, and the hover says `3 reads running could not list the checks: list_pipelines: 403 Forbidden`

### Requirement: A red round goes back to the session
`FlowRules.readLands` SHALL send the task back to its session and relay the failing job's lines as `<checks>`, a clue; read twice they MUST read the
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
The round brief (`RoundBrief.of`), relayed every round, SHALL prescribe one block per thread: its link, the quoted
newest note, `FIXED | NO CHANGE | QUESTION`, reply.

#### Scenario: Replies too long
- **WHEN** replies run long
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
A line typed at the replies report SHALL be said (`AgentSessions.say` → `nudgeTaskWindow`), never relayed: a relay overwrites `task_context.md`.

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

#### Scenario: Other branch convention, or target deleted
- **WHEN** its branch follows another convention (`feature/x`), or its target branch was deleted since
- **THEN** taken over as-is; it works, only the next `ship` needs a target

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

### Requirement: An unchanged round is relayed once
`TaskStops.round` SHALL keep a digest of the last round brief relayed into a task and the round's note fingerprint:
the sorted pairs of each open thread's link and newest note id, as the host gives them. A brief equal to the digest
SHALL NOT be relayed again, whatever overwrote `task_context.md` in between. Neither SHALL a round with open threads
whose fingerprint equals the last one's while the checks are red in neither. A `lift` SHALL forget both.

#### Scenario: A ship between two equal reads
- **WHEN** ABC-42 ships and the next poll reads the same threads as the round before
- **THEN** the sweep says the round is unchanged and no fresh session starts

#### Scenario: A reviewer's concession is not a loop
- **WHEN** a thread's newest note is the reviewer's and two polls read it unchanged, the copied text differing
- **THEN** only the first poll relays, and the second says the round is unchanged
