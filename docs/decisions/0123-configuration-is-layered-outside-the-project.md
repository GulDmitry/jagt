# 0123 — Configuration is layered, and every place jagt writes is configured

[← decisions](README.md) · rule: [`components`](../rules/components.md#whoever-works-on-jagt-reads-the-same-file-and-reaches-the-same-server)
· builds on [0120](0120-jagt-yml-is-edited-as-text-one-block-per-project.md), [0122](0122-a-role-is-a-module-the-chart-composes.md)

## Status

Proposed 2026-10-31 by the owner's direction (jagt grows towards an IDE). Not scheduled: 0122's roles live in jagt's root until it lands.
Superseded by [0126](0126-a-role-is-a-folder.md): roles first, the location map only when an install needs it.

## Context

- Today every setting sits in jagt's own checkout: `jagt.yml` over `jagt.yml.dist`, a brief's copy over its `.dist`
  (`HumanCopy`), `state.json` beside them. An installed app has no writable checkout.
- The owner wants a desktop app to keep settings, overrides, stats and artifacts outside the project, and today's
  layout to keep working unchanged.

## Decision

- **Where each kind of thing lives is configuration**: one `locations` map names a place per kind — config, briefs and
  cases, roles, state, logs, stats, eval output, artifacts. One resolver answers every path; no code builds one itself.
- **The defaults are today's places**: jagt's root and the project as now, so nothing changes until a key is set.
  A desktop install sets the user directories instead; both are values of the same map.
- A place may be inside the project repository when configured so; the default writes nothing new there.
- Four config layers, later wins per key: **shipped** (`*.dist`, read-only), **user**, **project shared** (a `.jagt/`
  the team commits, opt-in), **project mine** (per project under the user layer). Today the user layer is jagt's root.
- The resolver keeps each value's origin; the board shows it, and an edit lands in the layer it names via 0120's writer.
- Each OS's user directories are an `adapter/` strategy (macOS Application Support, Linux XDG); `JAGT_CONFIG_HOME`
  overrides.
- A brief, a case book and a role resolve through the same layers: `HumanCopy` becomes the general rule.
- No layer holds a secret: a key names an environment variable or keychain entry, never a value.

## Rejected

- Fixed user directories for everything: changes how jagt works today, and a team may want artifacts in the repo.
- One file with per-project sections only: no home for a project's own briefs, cases and roles.
- A database: the human's comments and order in a text file are theirs (0120).

## Reopen when

- A team needs a setting enforced over the user's (a policy layer above project mine).
- Two machines must share the user layer (a sync story).
