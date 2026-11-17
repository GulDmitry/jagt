import assert from "node:assert/strict";
import { mock, test } from "node:test";

const FILES = {
  "i18n/en.json": {
    due: "due",
    "meta.next-poll": "next poll {when}",
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
mock.method(globalThis, "fetch", async (/** @type {string} */ url) =>
  url in FILES
    ? new Response(
        JSON.stringify(FILES[/** @type {keyof typeof FILES} */ (url)]),
      )
    : new Response("", { status: 404 }),
);
const { classes, marked, requestLines, statusTip, watchLine } =
  await import("../components/facts.js");

/** @param {object} fields */
const task = (fields) => /** @type {never} */ (fields);

test("noLineIsShown whenTheWatchHasNoNote", () => {
  assert.equal(watchLine(task({ state: "STOPPED", note: "" }), 0), null);
});

test("noLineIsShown whenThereIsNoWatch", () => {
  assert.equal(watchLine(undefined, 0), null);
});

test("thePollIsDue whenItsTimeHasPassed", () => {
  const watch = task({ state: "WATCHING", note: "n", nextPollAt: 100 });

  assert.deepEqual(watchLine(watch, 200), { line: "next poll due" });
});

test("thePollIsDue whenItsTimeIsNow", () => {
  const watch = task({ state: "WATCHING", note: "n", nextPollAt: 100 });

  assert.deepEqual(watchLine(watch, 100), { line: "next poll due" });
});

test("thePollCountsDown whenItsTimeIsAhead", () => {
  const watch = task({ state: "WATCHING", note: "n", nextPollAt: 30000 });

  assert.deepEqual(watchLine(watch, 0), { line: "next poll 30s" });
});

test("theWatchIsStalled whenItIsNotWatching", () => {
  const watch = task({ state: "STOPPED", label: "Review", note: "no answer" });

  assert.deepEqual(watchLine(watch, 0), {
    line: "Review — no answer",
    stalled: true,
  });
});

test("theChecksAreMarked whenThePipelineHasAColour", () => {
  assert.deepEqual(
    ["RED", "RUNNING", "GREEN", "NONE", undefined].map((pipeline) =>
      marked(task({ pipeline })),
    ),
    [true, true, true, false, false],
  );
});

test("onlyTheNamesAreJoined whenSomeAreFalsy", () => {
  assert.equal(classes("a", false, null, undefined, "b"), "a b");
});

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
