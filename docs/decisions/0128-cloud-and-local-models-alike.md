# 0128 — Cloud and local models alike

[← decisions](README.md) · rule: [`ARCHITECTURE.md`](../../ARCHITECTURE.md#the-agent-system-is-a-ring)

## Status

Proposed 2026-11-02 by the owner. Accepted with the first task run through jagt's whole cycle on a local model.
Amended by [0135](0135-tool-less-reads-may-call-a-local-model-directly.md).

## Context

- Every session today runs on a cloud model. Access, terms and accounts can change under jagt (habr 1100830,
  read 2026-11-02: cloud account blocks, a consumer licence the owner reads as barring commercial use).
- jagt reaches a model only through an agent CLI, so the model behind a session is already the CLI's configuration.

## Decision

- jagt targets cloud providers and local models alike: which model serves a session is the agent CLI's own setting
  (provider, base URL), never jagt code. No ring above `adapter/` names a provider or a model host.
- A feature built on a cloud trait names the trait and works without it:
  - prompt cache: `cacheLifetime: 0` turns cold prompts off;
  - usage limits: no limit read is no stop, and nothing waits for a reset;
  - spend: an unknown price reads as unknown, never as zero;
  - context window: a session's start must fit the window the install declares.
- The proof is one task through start, plan, question, round and ship on a local model behind an unchanged CLI.

## Rejected

- A model client of jagt's own: it would duplicate the CLI's tool loop, the part a local model fails first.
- Cloud only: the owner wants a fallback that needs no account.

## Reopen when

- A local run shows the cycle cannot pass on a model this machine class can serve (32 GB): the target narrows to the
  hardware it needs.
