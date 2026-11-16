## ADDED Requirements

### Requirement: The server starter sits behind a TLS ingress
`jagt.server.yml.dist` SHALL set `server.forward-headers-strategy: native`, `server.servlet.session.cookie.secure: true` and
`server.servlet.session.timeout: 30m`. `kubernetes.yml.dist`'s Ingress SHALL carry a `tls` block for its host with a Secret of its own.
#### Scenario: A login behind the ingress
- **WHEN** the ingress ends TLS and forwards `X-Forwarded-Proto: https` and `X-Forwarded-For`
- **THEN** redirects keep `https`, the session cookie is Secure, and a login's peer address is the client's
#### Scenario: An idle board
- **WHEN** a login is idle for 30 minutes
- **THEN** its session ends and the next request lands on the login page
