## Why

A review bot the host does not flag read as a person unless the project's own `reviewBots` listed it, so a round
answering it waited on the human.

## What Changes

- One top-level `codeReview.reviewBots` every project reads; a project's own list adds to it.
- A `reviewReplyAuthors` account typed a person and in no bot list is warned once, naming `codeReview.reviewBots`.
- No bot is guessed from its name.
