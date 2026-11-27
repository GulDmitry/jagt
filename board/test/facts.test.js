import assert from "node:assert/strict";
import { test } from "node:test";
import { serveFiles } from "./fakefetch.js";

const FILES = {
  "i18n/en.json": {
    due: "due",
    "meta.next-poll": "next poll {when}",
  },
};
serveFiles(FILES);
const { classes, marked, watchLine } = await import("../components/facts.js");

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
