## ADDED Requirements

### Requirement: A value may come from the environment or a file
A string in `jagt.yml` SHALL take `${VAR}`, `${VAR:default}` and `${file:/path}`, then `JAGT__a__b=value` SHALL set the key `a.b`.
A change in a variable or file a read used SHALL be picked up by the next one. A resolved value SHALL never be logged.

#### Scenario: A variable fills a value
- **WHEN** a value is `${TOKEN}` and `TOKEN` is set
- **THEN** the key reads its value, and no log line carries it

#### Scenario: A file fills a value
- **WHEN** a value is `${file:/run/secrets/x}`
- **THEN** the key reads that file's content, trailing newline cut

#### Scenario: A variable overrides a key
- **WHEN** `JAGT__server__publicUrl` is set
- **THEN** it wins over `jagt.yml`'s `server.publicUrl`

#### Scenario: A variable that is not set
- **WHEN** a placeholder names an unset variable with no default
- **THEN** the start is refused naming the key and the variable

#### Scenario: Nothing to expand
- **WHEN** `jagt.yml` holds no placeholder and no `JAGT__` variable is set
- **THEN** it loads as before
