## Why
With `codeHost: command` the driver gave no note ids, so a round with threads never repeated and was relayed each poll.

## What Changes
- The `command` code-host driver reads a request's note ids from a configured `notesCommand`, one id per line.
- Unset keeps today's behaviour: no ids.

## Capabilities

### Modified Capabilities

- `review`: the `command` driver gives the note ids an unchanged round is recognised by.
