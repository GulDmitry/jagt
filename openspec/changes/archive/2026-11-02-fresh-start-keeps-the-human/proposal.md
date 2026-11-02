## Why

A review round's fresh start closed windows while the human used them. Job results they had sent, held unread by the
CLI, died with the window. A question put to the next session was killed mid-answer.
The fresh session read only the notes and the round, so the human's words were gone.

## What Changes

- A fresh start waits while the window holds the human's unsent input or a turn their own prompt opened.
- The wait ends once the human is gone for two seconds, the window dies or the task is removed.
- Any fresh start with an old transcript carries the old conversation beneath its instructions, not only a cold prompt.

## Capabilities

### Modified Capabilities

- `cold-prompts`: the carried block belongs to every fresh start, and a fresh start waits for the human.
