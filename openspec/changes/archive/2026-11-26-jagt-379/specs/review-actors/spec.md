## ADDED Requirements

### Requirement: An account is a bot where the host or a list says so
An author SHALL read bot where the host flags it, or where `codeReview.reviewBots` or the project's own `reviewBots` lists it;
both lists apply. A name SHALL never be guessed. `reviewReplyAuthors` says whom a reply goes to, never what an author is.
A `reviewReplyAuthors` account the host typed a person and no list names SHALL be warned once, naming `codeReview.reviewBots`.

#### Scenario: R1 A shared bot, a project with none
- **WHEN** `codeReview.reviewBots` names an account and a project lists none
- **THEN** that project's notes by it read bot

#### Scenario: R2 Both lists apply
- **WHEN** a project lists its own `reviewBots`
- **THEN** the shared and the project's accounts both read bot

#### Scenario: R3 A reply author typed a person
- **WHEN** `reviewReplyAuthors` names an account the host typed a person and no bot list names it
- **THEN** one warning names `codeReview.reviewBots`
