## Why

The Master's reviewer could call every server's `query*` and `fetch*`, one of which sends free text to a third
party, so a diff could ship the code anywhere; a database server's `query` runs SQL
([0027](../../../../docs/decisions/0027-a-headless-call-is-fenced-by-jagt.md)).

## What Changes

- The reviewer loads no MCP server unless `assistant.allowedTools` names MCP tools, and calls those alone.
- `query*` is no read verb.

## Capabilities

### Modified Capabilities

- `master`: the reviewer's MCP tools are the ones the human names.
