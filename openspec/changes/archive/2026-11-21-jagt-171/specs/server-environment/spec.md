## ADDED Requirements

### Requirement: The configuration is on the volume
The image SHALL read and write `jagt.yml` at `/data/jagt.yml`. A start finding no file there SHALL copy the image's `jagt.server.yml.dist` to it; a start finding one SHALL leave it alone.

#### Scenario: A project added, then a recreate
- **WHEN** a project is added from the board and the container is recreated on the same volume
- **THEN** the project is still in `jagt.yml`

#### Scenario: A first start
- **WHEN** the volume holds no `jagt.yml`
- **THEN** the image's starter is copied there

#### Scenario: A first start after an upgrade
- **WHEN** the volume holds no `jagt.yml` and one is mounted at `/app/jagt.yml`
- **THEN** the mounted file is copied there instead of the starter
