## Why
A project fed from a plain Markdown file needs no model, credential or token to read it.

## What changes
- `tracker.workflow: file` with `tracker.file` reads the file's items itself; startup refuses it without the file.
- A key the file holds is answered from it; any other reference is read as before.
- An unreadable file skips the poll.
