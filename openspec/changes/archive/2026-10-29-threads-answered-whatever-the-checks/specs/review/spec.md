## ADDED Requirements

### Requirement: Threads are answered whatever the checks say
A session's hand-back to REVIEW_PENDING, whatever its outcome, SHALL be refused while the last round relayed into the task carried
threads and `review_replies.md` in the worktree was not written since that relay (`HandBackDue`). The refusal names how many threads
were relayed and that each awaiting an answer needs its block. A round relayed without threads owes none. The checks' state, red,
green or running, never decides whether a thread is answered.

#### Scenario: Red checks and an open thread
- **WHEN** a round carrying a failing job outside the code and a thread with the reviewer's new answer is relayed
- **THEN** the session writes the thread's block, sets `outcome=question` naming the job, and the hand-back is accepted

#### Scenario: Hand-back with no block written
- **WHEN** a session hands back after a round that carried 2 threads and `review_replies.md` is absent or older than that relay
- **THEN** it is refused, told 2 thread(s) were relayed and each needs its block

#### Scenario: Replies written since the relay
- **WHEN** `review_replies.md` was written after the relay
- **THEN** the hand-back is not refused for replies

#### Scenario: A round of checks alone
- **WHEN** the last round relayed carried no threads
- **THEN** no replies are owed

## MODIFIED Requirements

### Requirement: An unchanged round is relayed once
`TaskStops.round` SHALL keep a digest of the last round relayed into a task: the failing job and the pushed branch tip from git while the checks are
red, and each open thread's link and newest note id. A round equal to it SHALL NOT be relayed again, whatever the brief's wording; where git cannot
name the tip, or a job or note id is unknown, the brief is digested. Neither SHALL open threads with the last notes while the checks are red in
neither. A `lift` SHALL forget both. Threads and checks are two tracks: a change in either relays the round, so an unchanged red job never
holds back a thread with a new note, and unchanged threads never hold back a new failing job. While the last report asks the human, a sweep
SHALL hold back the checks, but a thread whose newest note is not the one the last round carried is relayed alone, without the checks.

#### Scenario: A ship between two equal reads
- **WHEN** ABC-42 ships and the next poll reads the same threads as the round before
- **THEN** the sweep says the round is unchanged and no fresh session starts

#### Scenario: A reviewer's concession is not a loop
- **WHEN** a thread's newest note is the reviewer's and two polls read it unchanged, the copied text differing
- **THEN** only the first poll relays, and the second says the round is unchanged

#### Scenario: The same failure in other words
- **WHEN** the same job fails on the same pushed tip
- **THEN** the round is not sent again, whatever the brief's wording

#### Scenario: Red checks over a question
- **WHEN** ABC-42's session asked the human and the checks are still red
- **THEN** the sweep relays nothing until the human answers

#### Scenario: A new note over an unchanged red job
- **WHEN** the same job still fails on the same tip and a thread's newest note is new
- **THEN** the round is relayed

#### Scenario: A new failing job over unchanged threads
- **WHEN** the threads and their newest notes are those of the last round and another job fails
- **THEN** the round is relayed

#### Scenario: A new note while a question about the checks stands
- **WHEN** ABC-42's session asked about a failing job and the reviewer then adds a note to a thread
- **THEN** the thread is relayed without the checks, and the question stands
