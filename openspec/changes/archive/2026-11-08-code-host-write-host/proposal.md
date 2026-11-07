## Why
A request link a session reports steered the code-host write: `remove-repo` closed any link under the human's login.

## What changes
- `closeRequest` takes the repository's remote and sends nothing unless the link is on the host the repository lives on.
- A hostless remote has no host a link can be on, so every link is refused while `codeHost` is not `none`.
