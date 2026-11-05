## Why

After a restart jagt asked every session holding an open turn to repeat its report, though most had written nothing
while jagt was down and lost nothing; each ask cost a session's context.

## What Changes

- jagt stamps every minute that it is alive (`AliveStamp`); the next start reads when it went down.
- The restart ask goes only to a session whose log was written after that stamp; with no stamp, to none.

## Capabilities

### Modified Capabilities

- `session-brief`: the restart ask needs a log written while jagt was down.
