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

test("firstWaitingTaskIsCurrent whenTheChoiceLeftTheViewAndAnAliasIsLinked", () => {
  assert.equal(current(LIST, "gone", "p3")?.id, "b");
});

test("linkedAliasPicksItsTask whenAnotherTaskComesFirst", () => {
  assert.equal(current(LIST, null, "p3")?.id, "c");
});

test("linkedIdPicksItsTask whenAnotherTaskComesFirst", () => {
  assert.equal(current(LIST, null, "c")?.id, "c");
});

test("noTaskIsCurrent whenNothingIsShown", () => {
  assert.equal(current([], null, null), null);
});

test("stepStopsAtTheEnds whenWalkingPastThem", () => {
  assert.equal(stepTarget(LIST, "c", 1), "c");
  assert.equal(stepTarget(LIST, "a", -1), "a");
  assert.equal(stepTarget(LIST, "a", 1), "b");
});

test("stepMovesOneTask whenWalkingInsideTheList", () => {
  assert.equal(stepTarget(LIST, "b", 1), "c");
  assert.equal(stepTarget(LIST, "b", -1), "a");
});

test("noStepTarget whenTheListIsEmpty", () => {
  assert.equal(stepTarget([], undefined, 1), undefined);
});

test("nextRequiredWraps whenNoneWaitsAfterTheCursor", () => {
  assert.equal(nextRequiredTarget(LIST, "c"), "b");
  assert.equal(nextRequiredTarget(LIST, "a"), "b");
});

test("nextRequiredSkipsTheCursor whenWaitingTasksLieOnBothSides", () => {
  assert.equal(nextRequiredTarget(LIST, "b"), "c");
});

test("noRequiredTarget whenTheListIsEmpty", () => {
  assert.equal(nextRequiredTarget([], undefined), undefined);
});
