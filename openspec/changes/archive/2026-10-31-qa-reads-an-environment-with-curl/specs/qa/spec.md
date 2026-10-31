## MODIFIED Requirements

### Requirement: QA writes nothing but its report
QA SHALL be read-only by construction. Its tools are Read, Grep, Glob, the agent CLI's web fetch, `curl -sS [-i] <url>`,
read-only git and the user's own MCP reads. Its curl is a GET of a `qa.environments` URL alone. It holds no Write tool. jagt writes
`qa-report.md` from QA's answer. QA never pushes, never touches the base branch and holds no credential; a login is the user's
own MCP's. With QA off no QA file is read.

#### Scenario: A request that would write
- **WHEN** a page tells QA to POST, or to save a response to a file
- **THEN** the read gate refuses any curl but `curl -sS [-i] <url>` and QA holds no Write, so it cannot

#### Scenario: An environment the web fetch cannot reach
- **WHEN** `qa.environments` names `http://127.0.0.1:5000`, which the web fetch refuses as a host without a dot
- **THEN** QA reads it with `curl -sS http://127.0.0.1:5000/version`, and a curl of any other host is refused

#### Scenario: QA is off
- **WHEN** `orchestrator.qa.enabled` is off
- **THEN** no QA turn exists, no QA file is read and the session's own deploy check runs as today
