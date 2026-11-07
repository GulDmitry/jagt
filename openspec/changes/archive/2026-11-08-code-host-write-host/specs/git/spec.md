## ADDED Requirements

### Requirement: A code-host write goes only to the repository's own host
`CodeHost.closeRequest` SHALL send nothing unless the request's address is on the host the repository lives on: its
`codeHostName`, else the host its remote names. A remote naming no host (a local path, `file:`) refuses every address
while `codeHost` is not `none`. The refusal is a failed answer, logged at WARN with its cause. Reads keep judging
no remote's links.

#### Scenario: A local remote and a foreign link
- **WHEN** ABC-42's repository has a local-path remote and a session reported a link on a public host
- **THEN** `remove-repo` fails naming the hostless remote, and no host CLI runs

#### Scenario: Another host, same project path
- **WHEN** the link is on a second host whose group and project match the remote's
- **THEN** `remove-repo` fails naming the other host, and no host CLI runs
