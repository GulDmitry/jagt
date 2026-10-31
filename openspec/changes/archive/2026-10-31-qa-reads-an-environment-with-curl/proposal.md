## Why

The web fetch refuses a host without a dot and upgrades http to https, so QA could not read a local or plain-http environment.

## What Changes

- QA may run `curl -sS [-i] <url>` on a `qa.environments` URL; the read gate refuses every other curl.

## Capabilities

### Modified Capabilities

- `qa`: the tools a QA read holds.
