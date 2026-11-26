# review Specification

## Purpose

How a review round is answered, reported and drafted into replies, how a request is taken over, and how the code reviewer's findings reach it.

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

### Requirement: The outcome is a field
`update_agent_status` SHALL take `outcome` (`question` | `no_changes` | `progress`) and `reviewRequestUrl`, the brief ending
all three at REVIEW_PENDING. What a report means SHALL be the task's `task/turn/AgentReport`, never a marker read out of its
line; an outcome typed into the message is words. A `state.json` from before SHALL read it once from the line's marker.
Over uncommitted work `no_changes` SHALL become a round with a diff (`WorktreeChanges`, one `git status` per report).
#### Scenario: A marker typed into the message
- **WHEN** a session reports `outcome=question: which branch?` as its message and no `outcome`
- **THEN** the line reads as written, and nobody is asked
- **WHEN** jagt starts on a `state.json` whose line reads `awaiting: which database`
- **THEN** the card reads `NEEDS INPUT: which database`
#### Scenario: A round comes back clean or approved
- **WHEN** a round comes back clean, unapproved
- **THEN** REVIEWED, nothing asked of you
- **WHEN** an approval arrives
- **THEN** you are tapped, the one time: it is yours to deploy
- **WHEN** the agent says it changed nothing but files changed
- **THEN** not believed: the round has a diff

### Requirement: The command driver reads note ids from its notesCommand
The `command` code-host driver SHALL read a request's unresolved note ids from the project's `notesCommand`, run with `{request}`
replaced, one id per line; unset, it gives none.
#### Scenario: The command host lists the notes
- **WHEN** `codeHost: command` has a `notesCommand` printing two ids and the next poll prints the same
- **THEN** the round is the same and is relayed once

### Requirement: Pipeline status is one of jagt's five words
`TaskState.pipelineStatus` SHALL be one of jagt's five words: `success`, `failed`, `running`, `none`, `unknown`; `flow/deploy/Pipeline` parses it.
A round reading no listing SHALL write nothing, the last word standing flagged. The third such round running since the `lift`
SHALL stop the task, its evidence the reads' words for why; a listing counts anew.
#### Scenario: No checks dot while the host shows a failed run
- **WHEN** no checks dot shows while the host shows a failed run
- **THEN** nobody read one yet; `none` means the host listed none
- **WHEN** a third poll running cannot list ABC-42's pipelines, `list_pipelines: 403 Forbidden`
- **THEN** jagt stops it, and the hover says `3 reads running could not list the checks: list_pipelines: 403 Forbidden`

### Requirement: A red round goes back to the session
`FlowRules.readLands` SHALL send the task back to its session and relay the failing job's lines as `<checks>`, a clue; read twice they MUST read the
same, or every poll re-briefs. A red the host calls infrastructure is not relayed: [infra-wait](../infra-wait/spec.md).
#### Scenario: Pipeline goes red
- **WHEN** the pipeline goes red
- **THEN** `checks failed` on the card, one notification; the session finds and fixes the cause
- **WHEN** checks are red, no comments
- **THEN** the agent fixes the build and hands back
- **WHEN** the red is not in the code, or nothing here reads it, and the host does not call it infrastructure
- **THEN** the session changes nothing and asks you, naming the job

### Requirement: The reply shape is prescribed once
The round brief (`RoundBrief.of`), relayed every round, SHALL prescribe one block per thread: its link, one author-kind line
(`person`, `bot` or `reviewer`, given by jagt from the note's author), the quoted newest note, `FIXED | NO CHANGE | QUESTION`,
reply. Which threads a round carries and who answers them is [review-actors](../review-actors/spec.md).
#### Scenario: Replies too long
- **WHEN** replies run long
- **THEN** the agent broke its brief; a re-`sweep` re-briefs

### Requirement: The replies report reads the file
`replies [task]` (`command/ReviewRepliesReport`) SHALL print every block off the file, never the badge, unrecognised shape
included. Its only button SHALL be the Drafted review tab, never the card (`GlobalCommand.aboutOneTask`).
A line typed at it SHALL be said (`AgentSessions.say` → `nudgeTaskWindow`), never relayed: a relay overwrites `task_context.md`.
#### Scenario: What will be posted
- **WHEN** you ask what will be posted
- **THEN** `replies <task>` or the Drafted review tab shows every draft, and the card has no replies button
#### Scenario: A drafted reply is wrong
- **WHEN** you say so under the open `replies` report
- **THEN** the session answers into the same report

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
- **THEN** refused as unread, never missing, naming what stopped the read on one line
#### Scenario: No such request
- **WHEN** the host answers there is no such request
- **THEN** refused in those words: the one case "does not exist" is said

### Requirement: GitHub approval without a required review
A GitHub request with no review required SHALL count as approved once someone approves.
#### Scenario: Approved, not required
- **WHEN** someone approves where no review is required
- **THEN** approved

### Requirement: An unchanged round is relayed once
`TaskStops.round` SHALL keep a digest of each round relayed: the failing job and the pushed tip while the checks are red,
and the sorted newest note ids. The host gives those ids (`CodeHost.unresolvedNotes`), never the model.
A round equal to the last one the session answered SHALL NOT be relayed again, whatever the brief's wording,
unless the task reported `PLAN_PENDING` since. A `lift` SHALL forget the digests.
#### Scenario: A ship between two equal reads
- **WHEN** ABC-42 ships and the next poll reads the same threads as the round before
- **THEN** the sweep says the round is unchanged and no fresh session starts
#### Scenario: A reviewer's concession is not a loop
- **WHEN** a thread's newest note is the reviewer's and two polls read it unchanged, the copied text differing
- **THEN** only the first poll relays, and the second says the round is unchanged
#### Scenario: The same failure in other words
- **WHEN** the same job fails on the same pushed tip
- **THEN** the round is not sent again, whatever the brief's wording
#### Scenario: The model writes other ids
- **WHEN** two polls of one request have the model write different note ids and the host gives the same ones
- **THEN** the round is the same and is relayed once

### Requirement: An unknown id digests the brief
Where the repository cannot name the tip, or a job or note id is unknown, the brief SHALL be digested.
A failed host read SHALL leave the ids unknown, with a warning naming the cause.
Neither SHALL open threads with the last notes while the checks are red in neither.
#### Scenario: A tip or an id is unknown
- **WHEN** the pushed tip cannot be named from the repository, or the host read failed, while the checks are red
- **THEN** the brief is digested, and a failed host read logs a warning naming the cause

### Requirement: Threads and checks are judged on their own
Threads and checks SHALL be two tracks, each judged on its own. While the last report asks the
human, a sweep SHALL hold the checks and every thread it carried; a thread with a new newest note is relayed alone.
#### Scenario: A new note over a question about the checks
- **WHEN** ABC-42's session asked about a failing job and a reviewer then adds a note to a thread
- **THEN** that thread is relayed without the checks, and the question stands

### Requirement: An unchanged request is not read again by a poll
`ReviewRound` SHALL keep each request's last paid read with the host's fingerprint (`CodeHost.requestFingerprint`) and note ids.
An auto-review poll finding both unchanged SHALL keep that verdict unpaid; anything else SHALL read as before.
#### Scenario: A poll of an unchanged request
- **WHEN** an auto-review poll finds ABC-42's fingerprint and note ids as the last paid read left them
- **THEN** no model is asked
- **WHEN** the fingerprint or a note changed, the host gives none, or you `sweep` ABC-42
- **THEN** the request is read again

### Requirement: Review bots are answered, bounded per thread
The role SHALL answer a review bot's note once per note id: fix, change nothing and say why, or ask. It SHALL answer a thread at most three times.
A thread past the bound SHALL be exhausted until it resolves or the request closes. The role SHALL never answer its own newest note.

#### Scenario: A bot answers a fourth time
- **WHEN** a bot answers the role's answer a fourth time
- **THEN** nothing is posted and the thread is exhausted until it resolves or the request closes

#### Scenario: The newest note is the role's own
- **WHEN** the newest note of a thread is the role's own
- **THEN** nothing is posted

### Requirement: The code reviewer reads a task's diff before its ship
The code reviewer SHALL read the diff of a task at REVIEW_PENDING holding no request once per tree state, relaying each finding as a round.
`FlowRules` SHALL refuse SHIP, the Master's too, while a finding awaits its block in `review_replies.md` or the tree is unread.
A finding raised there SHALL not be posted again on the request. A failed read, a reviewer switched off or an empty diff SHALL never hold a SHIP.

#### Scenario: A session hands back
- **WHEN** ABC-42's session reports REVIEW_PENDING and the Master presses SHIP before its diff was read
- **THEN** SHIP is declined, and once read each finding reaches the session as a review round that holds SHIP until its block is written

#### Scenario: A finding already raised
- **WHEN** ABC-42's request is read and a finding is one the pre-ship read raised
- **THEN** no note is posted for it

### Requirement: A round with nothing new says so on its verb
A `sweep` reading the round unchanged, or still waiting, SHALL answer as work not done.
The board SHALL draw that verb's own button with a dashed muted edge, its hover carrying the answer.
The mark ends when the task's status moves or the verb is pressed again.
#### Scenario: Nothing new
- **WHEN** the human presses Check review on ABC-42 and the round reads unchanged
- **THEN** the button wears the dashed edge and the toast says so
#### Scenario: The card moves
- **WHEN** ABC-42's status changes, or the human presses Check review again
- **THEN** the dashed edge is gone
