## Why
The cap of 24 open tasks was fixed in code; the owner wants 50 by default, set by the person running jagt.

## What Changes
- At most `maxTasks` tasks are open, 50 by default; `maxTasks` is a key of `jagt.yml`, re-read on every access.
