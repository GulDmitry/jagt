## Why

A server holds no hand-edited `jagt.yml`: its secrets are variables and mounted files, and one key is set per container.

## What Changes

- `jagt.yml` string values take `${VAR}`, `${VAR:default}` and `${file:/path}`.
- `JAGT__a__b=value` sets the key `a.b` after that, for `jagt.yml`'s own sections and for the keys Spring binds alike.
- The hot re-read counts the variables and files it used; an unset variable with no default refuses the start.

## Impact

- `startup`
