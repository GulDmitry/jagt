## MODIFIED Requirements

### Requirement: Drafted replies are a file
`TaskViews` SHALL stat `review_replies.md` for presence, never a count; `ReviewDrafts.pending` SHALL want it newer than
`mrCreatedAt`. jagt SHALL never delete the file; the agent is asked to.
`CodeReviewConfig.shipPostsEveryDraft` SHALL decide when drafts are spent: under `postReviewReplies=false` or a partial
`reviewReplyAuthors` filter none is, and the announcement stands, whatever the status, until the human posts or ends them.
The your-move notification SHALL say drafted replies wait whatever the round reported.
#### Scenario: Stale reply file
- **WHEN** the file predates `mrCreatedAt`
- **THEN** nothing is pending
#### Scenario: Posting off
- **WHEN** you `ship` under `postReviewReplies=false`
- **THEN** the announcement stands
#### Scenario: Drafts a ship left
- **WHEN** a ship leaves drafted replies to a person's note unposted and the task leaves the hand-back
- **THEN** the card still shows them pending
#### Scenario: No code changed
- **WHEN** a round reports `NO_CHANGES` and drafted replies wait
- **THEN** the your-move notification says drafted replies wait

## ADDED Requirements

### Requirement: A thread relays only what follows the answer
A round SHALL relay of an open thread only the notes after the newest one by the account the host's tools act as, a reply posted by hand
or from a draft included.
#### Scenario: Answered by hand
- **WHEN** you answer ABC-42's thread by hand and the reviewer writes again
- **THEN** the next round relays the thread's link and the reviewer's new note, not the notes before your answer
