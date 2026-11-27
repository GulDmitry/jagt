import assert from "node:assert/strict";
import { test } from "node:test";
import {
  pickFacts,
  quickClass,
  rowClass,
  stoppedLine,
} from "../components/rows.js";

/** @param {object} fields */
const task = (fields) => /** @type {never} */ (fields);

test("theQuickVerbIsPrimary whenTheActionIs", () => {
  assert.deepEqual(
    [{ primary: true }, {}].map((action) => quickClass(task(action))),
    ["row-verb primary", "row-verb"],
  );
});

test("theRowNamesItsOwnerAndState whenOptionalAndSelected", () => {
  const row = rowClass(task({ owner: "AGENT", attention: "OPTIONAL" }), true);

  assert.equal(row, "row agent optional selected");
});

test("theRowIsPlain whenRequiredAndNotSelected", () => {
  const row = rowClass(task({ owner: "HUMAN", attention: "REQUIRED" }), false);

  assert.equal(row, "row human");
});

test("noLineIsStopped whenTheWatchRunsOnSchedule", () => {
  const watching = {
    autoReview: { state: "WATCHING", note: "n", nextPollAt: 10 },
  };

  assert.equal(stoppedLine(task(watching), 0), null);
});

test("theStopIsSaid whenTheWatchStalled", () => {
  const stalled = {
    autoReview: { state: "STOPPED", label: "Review", note: "no answer" },
  };

  assert.equal(stoppedLine(task(stalled), 0), "Review — no answer");
});

test("theFactsListEveryUnprintedFact whenChangedAndChecked", () => {
  const label = pickFacts(
    task({
      alias: "a1",
      id: "ABC-42",
      title: "Fix",
      statusLabel: "In work",
      pipeline: "RED",
      pipelineSaid: "tests fail",
    }),
    true,
    "stopped",
  );

  assert.equal(label, "checks: tests fail, stopped, list.changed");
});

test("theChecksAreLowercased whenThePipelineSaidNothing", () => {
  const label = pickFacts(
    task({ id: "ABC-42", statusLabel: "Done", pipeline: "RED" }),
    false,
    null,
  );

  assert.equal(label, "checks: red");
});

test("noLineIsStopped whenTheTaskHasNoWatch", () => {
  assert.equal(stoppedLine(task({}), 0), null);
});
