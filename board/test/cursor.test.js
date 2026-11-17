import assert from "node:assert/strict";
import { test } from "node:test";
import { current, nextRequiredTarget, stepTarget } from "../ui/cursor.js";

const LIST = /** @type {never[]} */ ([
  { id: "a", alias: "p1", attention: "NONE" },
  { id: "b", alias: "p2", attention: "REQUIRED" },
  { id: "c", alias: "p3", attention: "REQUIRED" },
]);

test("chosenTaskIsCurrent whenItIsStillShown", () => {
  assert.equal(current(LIST, "c", null)?.id, "c");
});

test("linkedAliasIsCurrent whenNothingWasChosen", () => {
  assert.equal(current(LIST, null, "p1")?.id, "a");
});

test("firstWaitingTaskIsCurrent whenTheChoiceLeftTheView", () => {
  assert.equal(current(LIST, "gone", null)?.id, "b");
});

test("noTaskIsCurrent whenNothingIsShown", () => {
  assert.equal(current([], null, null), null);
});

test("stepStopsAtTheEnds whenWalkingPastThem", () => {
  assert.equal(stepTarget(LIST, "c", 1), "c");
  assert.equal(stepTarget(LIST, "a", -1), "a");
  assert.equal(stepTarget(LIST, "a", 1), "b");
});

test("nextRequiredWraps whenNoneWaitsAfterTheCursor", () => {
  assert.equal(nextRequiredTarget(LIST, "c"), "b");
  assert.equal(nextRequiredTarget(LIST, "a"), "b");
});
