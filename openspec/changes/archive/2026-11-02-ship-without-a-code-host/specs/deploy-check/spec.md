## MODIFIED Requirements

### Requirement: The code host is chosen
jagt SHALL read a deploy's pipeline through the driver `codeHost` names, a project's key over `orchestrator.codeHost` over `github`.
The drivers are `github` (`gh`), `gitlab` (`glab`), `command`, `none` and `replay`. jagt MUST NOT guess the kind from a host name or an
address, and the agent chosen by `agent.cli` selects none. `replay` reads every commit green and re-runs nothing.
`codeHostName` names the host where the remote does not. `gh` is told the host with `-R <host>/<owner>/<repo>`.
`glab` keeps a port the remote has in `--hostname`. A CLI or command holds the credential, jagt none.

#### Scenario: No key
- **WHEN** a project and `orchestrator` name no `codeHost`
- **THEN** its pipelines are read through `gh`

#### Scenario: A host the name does not give away
- **WHEN** `projects.proj.codeHost` is `gitlab` and the remote is `git@git.example.invalid:acme/shop.git`
- **THEN** its pipelines are read through `glab` on `git.example.invalid`

#### Scenario: An enterprise host
- **WHEN** the remote is `git@ghe.example.invalid:acme/shop.git` and `codeHost` is `github`
- **THEN** `gh` is run with `-R ghe.example.invalid/acme/shop`

#### Scenario: Any provider
- **WHEN** `codeHost` is `command` and `pipelineCommand` is `["ci-state", "{sha}"]`
- **THEN** it runs in the project's directory with the deploy commit
- **AND** what it prints, one of `success`, `failed`, `running`, `none`, `unknown`, is the pipeline
- **AND** anything else leaves the deploy open as unread, naming `pipelineCommand`

#### Scenario: No code host
- **WHEN** `codeHost` is `none`
- **THEN** no pipeline is read and the deploy stands as for a repository with no code-host address

