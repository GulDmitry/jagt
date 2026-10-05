# 0011 — A red run is the session's to diagnose

[← decisions](README.md) · rule: [`review.md`](../rules/review.md#a-round-reports-its-outcome-as-a-field-not-as-a-turn-of-phrase)

## Status

Accepted 2026-10-05.

## Context

A request's pipeline failed on a quality gate in a child pipeline. The read quoted the parent's trigger job, the
brief said only "fix the failing build", the task stayed CI_POLLING owned by the host, and the board stayed quiet
for as long as the session did.

## Decision

- A red read stops a task waiting on the host at CI_FAILED (`FlowRules.readRed`): the human's move until the
  session takes it; a session already on it keeps its status.
- The read follows a trigger job into the pipeline it started, and reads a linked verdict over that tool's MCP.
- The brief hands the session a method: find the job, reproduce it, fix, re-run; what is not code or not
  readable is a question.

## Rejected

- Driving a browser by default: an MCP is exact, a page is scraped; the browser stays the last tool.
- A retry verb for a red that is not code: one more capability for what a question already reaches.

## Reopen when

- Sessions answer most red runs with a question about infrastructure: a retry verb then pays for itself.
