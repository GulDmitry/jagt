## MODIFIED Requirements

### Requirement: A fresh start carries what the old session knew
Beneath the instructions in `task_context.md`, a fresh start SHALL carry, gathered without a model: the last report or
open question, the commits and changed files since the base (40 lines each), and the old transcript's path.
It SHALL also carry the last human prompts and model texts of the old conversation. Those are newest kept within
16,000 characters, no tool input or output.
A fresh start replacing a session with no transcript SHALL carry the report and the git facts, and no conversation
or transcript path.

#### Scenario: A long conversation
- **WHEN** the old conversation holds more than 16,000 characters
- **THEN** the oldest are cut and no tool input or output appears

#### Scenario: A session with no transcript
- **WHEN** a fresh start replaces a session with no transcript
- **THEN** `task_context.md` holds the last report and the commits and files since the base,
  and no conversation or transcript path

#### Scenario: A round replaces a session the human talked to
- **WHEN** a round's fresh start replaces a session whose transcript holds the human's last words
- **THEN** `task_context.md` holds the round's instructions first, then the human's last words
