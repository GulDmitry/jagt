## Why

A human typing into a task session that sat idle overnight made the CLI rewrite its whole context uncached
(hundreds of k tokens, about 2% of a session limit). Decision 0001 already starts a fresh session for instructions
sent through jagt; a prompt typed in the window bypassed it.

## What Changes

- `prompt-gate=UserPromptSubmit` is answered: past `orchestrator.agent.cacheLifetime` idle, the prompt goes to a fresh session.
- It carries the last report, git's commits and stat, and the end of the old conversation.
- A turn end is refused once when `task_notes.md` is older than `orchestrator.agent.notesEvery`.

## Capabilities

### New Capabilities

- `cold-prompts`: the cold prompt, what it carries, and the notes kept current.

### Modified Capabilities

- `runtime`: a third answered hook.
