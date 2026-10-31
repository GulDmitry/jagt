# 0123 — Configuration is layered, and lives outside the project

[← decisions](README.md) · rule: [`components`](../rules/components.md#whoever-works-on-jagt-reads-the-same-file-and-reaches-the-same-server)
· builds on [0120](0120-jagt-yml-is-edited-as-text-one-block-per-project.md), [0122](0122-a-role-is-a-module-the-chart-composes.md)

## Status

Proposed 2026-10-31 by the owner's direction (jagt grows towards an IDE). Built before 0122's declarative roles.

## Context

- Today every setting sits in jagt's own checkout: `jagt.yml` over `jagt.yml.dist`, a brief's copy over its `.dist`
  (`HumanCopy`), `state.json` beside them. An installed app has no writable checkout.
- The owner wants every setting and override stored outside the project, per user and per project, and user roles.

## Decision

- Four config layers, later wins per key:
  1. **shipped**: the `*.dist` files inside the jar, read-only;
  2. **user**: the user's config directory, `jagt.yml`, briefs, cases, `roles/`;
  3. **project, shared**: a `.jagt/` the project's team commits, read-only to jagt, opt-in per project;
  4. **project, mine**: `<user config>/projects/<key>/`, the same files for one project.
- jagt writes into no project repository; an edit lands in layer 2 or 4 alone, through 0120's text-preserving writer.
- One resolver merges the layers and keeps each value's origin; the board shows it and edits the layer it names.
- State is not config: `state.json`, logs and eval stats move to the user's state directory.
- Each OS's directories are an `adapter/` strategy (macOS `~/Library/Application Support/jagt`, Linux XDG), with
  `JAGT_CONFIG_HOME` overriding.
- A brief, a case book and a role resolve through the same layers: `HumanCopy` becomes the general rule.
- No layer holds a secret: a key names an environment variable or keychain entry, never a value.
- Getting started stays one command: the first start writes the user `jagt.yml` from the shipped one.
- The checkout's own `jagt.yml` is read as layer 2 while it exists, so nothing breaks on upgrade.

## Rejected

- Config inside each project: the repository is the team's, and jagt would write into it.
- One file with per-project sections only: no home for a project's own briefs, cases and roles.
- A database: the human's comments and order in a text file are theirs (0120).

## Reopen when

- A team needs a setting enforced over the user's (a policy layer above 4).
- Two machines must share layer 2 and 4 (a sync story).
