## Why

A running jagt, on a server included, could be debugged only by a shell on its machine.

## What Changes

- `read_record` and `read_transcript` on the MCP server, `GET /api/debug/records/{record}` and `GET /api/debug/transcript` under `/api`.
- Records: the backend log, the Master's feed, `stops.jsonl`, `stops-reviewed.jsonl`; state and reports stay on `list_tasks` and `GET /state`.
- Under console logging the log read returns the lines stdout carried.

## Impact

- `debug-reads`: new capability, "A running jagt is read, never changed, through its own door"; `protocol` is at its page budget.
