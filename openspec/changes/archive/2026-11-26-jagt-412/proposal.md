## Why

A deployed task's session reported IN_PROGRESS while the deploy branch's pipeline still ran; the pipeline then failed and
nobody read it, because only a DEPLOYED task's deploy is read. A press after it reset the deploy's recorded push.

## What Changes

- A session's report off DEPLOYED is refused until the pipeline on each landed tip read green and no checker reads it.
- A red deploy pipeline handed back names each failed job and its reason.
- A new press keeps the push and pipeline reads a live deploy recorded; only a new landing replaces them.
