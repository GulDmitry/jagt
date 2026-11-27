import assert from "node:assert/strict";
import { test } from "node:test";
import { serveFiles } from "./fakefetch.js";

const FILES = {
  "i18n/en.json": {
    "meta.request.opened": "opened {at}",
    "meta.request.undated": "undated",
    "meta.approved": "approved",
    "meta.not-approved": "not approved",
    "meta.shipped": "shipped {n} times",
    "meta.checks": "checks {said}",
    "meta.checks.unread": "unread",
    "meta.live": "live",
  },
};
serveFiles(FILES);
const { requestLines, statusTip } = await import("../components/facts.js");

/** @param {object} fields */
const task = (fields) => /** @type {never} */ (fields);

test("theApprovalAndShipsAreNamed whenTheRequestWasShippedTwice", () => {
  const lines = requestLines(
    task({
      approved: true,
      pipeline: "GREEN",
      history: [
        { status: "SHIPPING" },
        { status: "SHIPPING" },
        { status: "X" },
      ],
    }),
    1763370300000,
    null,
  );

  assert.deepEqual(lines, [
    "opened 17 Nov 2025, 09:05 UTC",
    "approved",
    "shipped 2 times",
  ]);
});

test("noShipIsNamed whenTheRequestWasShippedOnce", () => {
  const lines = requestLines(
    task({ pipeline: "GREEN", history: [{ status: "SHIPPING" }] }),
    0,
    null,
  );

  assert.deepEqual(lines, ["undated"]);
});

test("theChecksAreSaid whenNoDotShowsThem", () => {
  const lines = requestLines(
    task({ pipeline: "NONE", pipelineSaid: "skipped" }),
    0,
    { line: "watching" },
  );

  assert.deepEqual(lines, ["undated", "checks skipped", "watching"]);
});

test("theChecksAreUnread whenNothingSaidThem", () => {
  const lines = requestLines(task({ pipeline: "NONE" }), 0, null);

  assert.deepEqual(lines, ["undated", "checks unread"]);
});

test("theRequestIsNotApproved whenTheTaskSaysSo", () => {
  const lines = requestLines(
    task({ approved: false, pipeline: "RED" }),
    1763370300000,
    null,
  );

  assert.deepEqual(lines, ["opened 17 Nov 2025, 09:05 UTC", "not approved"]);
});

test("theWorkIsLive whenDeployedWithoutADeployVerb", () => {
  const status = statusTip(
    task({ deployed: true, waiting: "for CI", actions: [{ id: "a" }] }),
    { line: "stalled" },
  );

  assert.deepEqual(status, {
    tip: "stalled\n\nfor CI\n\nlive",
    live: true,
  });
});

test("theWorkIsNotLive whenOneVerbDeploysItAgain", () => {
  const status = statusTip(
    task({
      deployed: true,
      actions: [{ id: "a" }, { id: "d", again: true }],
    }),
    null,
  );

  assert.deepEqual(status, { tip: "", live: false });
});
