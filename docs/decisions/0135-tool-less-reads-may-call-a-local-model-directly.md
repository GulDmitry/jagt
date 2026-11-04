# 0135 — Tool-less reads may call a local model directly

[← decisions](README.md) · rule: [`seams`](../rules/seams.md) · amends [0128](0128-cloud-and-local-models-alike.md)
· stands under [0130](0130-every-third-party-is-a-configured-seam.md)

## Status

Accepted 2026-11-04.

## Context

- 0128 rejected a model client of jagt's own because it would duplicate the CLI's tool loop; a read that uses no tool has no loop.
- Measured 2026-11-04, one machine, the command-mapping read:
  - an agent CLI with no tools on a local model: 1.1–1.7 s on a one-line prompt, but 13 s or a hang past 40 s on the real prompt;
  - a direct call with a `json_schema` response format: 4.0–4.9 s, and the answer matched the schema every time.
- `promptEval` command mapping, one case at a time: haiku 15 of 15 at ~10 s a call; Qwen3.5-4B Q6 and 9B Q4 12 of 15
  at ~1.5 s, both missing the editor, start-a-ticket and deploy rows, so quality is not yet at parity.
- One example per missed row in the prompt (2026-11-04): haiku stays 15 of 15, the 4B stays 12 of 15 — deploy right,
  a bare "send it for review" now `ship`; the examples were dropped.
- Run side by side, the local server queued past the timeout and every such call went to the cloud.

## Decision

- A read of a kind that reads no tool (`AssistantCallKind.readsTools()` false) may call an OpenAI-compatible endpoint directly,
  chosen in `jagt.yml` under `assistant.local`: `endpoint`, `model`, `timeout`, `kinds`.
- Off by default, per kind; the default stays the cloud until `promptEval` shows parity on that kind.
- A local call that fails, times out or answers outside the schema is asked of the cloud once, and says why in a WARN line.
- Startup refuses an endpoint that is no http(s) address, a missing `model`, and a listed kind that reads tools.
- Local tokens are booked at zero cost under the kind.
- The stand reads the same endpoint from `ORCHESTRATOR_ASSISTANT_LOCAL_ENDPOINT`/`_MODEL`: jagt under test inherits it,
  and the ticket rewrite asks it first, haiku where it fails, `rewrite.txt` naming which answered (owner 2026-11-04).

## Rejected

- Routing every kind to the local model: a read with tools needs the agent CLI's loop, the part a small model fails first.
- Retrying the local call: a second wait costs more than the cloud answer.
- A fallback that is the human's to choose per failure: a mapping read feels like typing, so it must still answer.

## Reopen when

- `promptEval` shows a local model at parity on a kind: the default may turn on for it.
- A read with tools runs on a local model through the agent CLI at a usable speed: this record narrows to the reads that need none.
