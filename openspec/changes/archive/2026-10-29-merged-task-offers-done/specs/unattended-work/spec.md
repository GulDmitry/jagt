## MODIFIED Requirements

### Requirement: An open request is what the poller watches
`AutoReviewCadence.polls` SHALL watch an open request, never a status: an unmerged one exists and the task is not DONE.
