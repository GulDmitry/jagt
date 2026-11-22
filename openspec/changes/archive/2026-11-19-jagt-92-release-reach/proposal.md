## Why
A release feed jagt cannot read is jagt's own reach failing; today it only logs a warning, so the board never says the update check is blind.

## What Changes
- A failed read of the release feed raises the head's one reach mark naming its driver and host; the next good read clears it.
- A check's start removes `.staging.*` folders an interrupted run left in the install root.
