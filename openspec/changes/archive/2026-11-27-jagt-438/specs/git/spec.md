## MODIFIED Requirements

### Requirement: ToolGate refuses the line
`ToolGate` (`POST /api/agent/tool`) SHALL refuse that push too. It also refuses a push naming no branch, deleting the task's
(`--de`, `-ud`) or forced unleased; `HEAD` unless bare and unmoved; `send-pack`, `http-push`, `receive-pack`; a `gh`/`glab`
call but a read, however wrapped (quoted as data, it runs); a line naming the board's port or the Master's token.
A refusal's push advice SHALL keep the `--force-with-lease` form the refused line held and say the push runs alone on its line.

#### Scenario: Skipping the hook
- **WHEN** a push line holds `--no-verify`, `git -c`, `core.hooksPath`, `alias.`, `GIT_CONFIG*`, `env -i`, `sh -c` or
  `eval`, however quoted; a git line holds `$'…'` or `$"…"`
- **THEN** refused

#### Scenario: A rebased branch behind a compound push line
- **WHEN** the base moved on, the task branch was rebased, and a line `find … -exec sh -c … ; git push --force-with-lease=<b>:<sha> origin <b>` is refused
- **THEN** the advice is `git push --force-with-lease=<b>:<sha> origin <b>` alone on its line, and that push clears the request's conflict
