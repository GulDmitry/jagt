## Why

The code host jagt reads for a deploy was guessed from the remote's host name, so a self-hosted host with no name to
read it from was read as the wrong kind. Every third party is a configured seam ([0130](../../../docs/decisions/0130-every-third-party-is-a-configured-seam.md)).

## What Changes

- `codeHost` chooses the driver jagt reads a pipeline through: `github` (default), `gitlab`, `command` or `none`, per project or for all.
- `codeHostName` names the host where a remote does not; `pipelineCommand` and `retryCommand` serve `command`.
- A host's words are told in jagt's five by its driver; the pipeline status jagt keeps is those five.
- Startup refuses a selected CLI or command that is not there.

## Capabilities

### Modified Capabilities

- `deploy-check`: the code host is chosen by `jagt.yml`; `none` settles a deploy without a pipeline.
- `review`: the pipeline status is jagt's five words.
