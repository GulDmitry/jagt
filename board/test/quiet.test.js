import assert from "node:assert/strict";
import { test } from "node:test";
import { clear, mark, sayingOn } from "../ui/quiet.js";

const sweep = /** @type {never} */ ({ id: "sweep" });
const task = (/** @type {string} */ id, /** @type {number} */ statusSince) =>
  /** @type {never} */ ({ id, statusSince, actions: [sweep] });

test("theVerbSaysItsAnswer whenItsPressChangedNothing", () => {
  mark(task("ABC-1", 100), sweep, "round unchanged");

  assert.equal(sayingOn(task("ABC-1", 100)).get("sweep"), "round unchanged");
});

test("theMarkIsGone whenTheCardsStateMoved", () => {
  mark(task("ABC-2", 100), sweep, "round unchanged");

  assert.equal(sayingOn(task("ABC-2", 200)).has("sweep"), false);
});

test("noVerbSpeaks whenTheTaskOffersNone", () => {
  const bare = /** @type {never} */ ({ id: "ABC-4", statusSince: 100 });

  assert.equal(sayingOn(bare).size, 0);
});

test("theMarkIsGone whenTheVerbIsPressedAgain", () => {
  mark(task("ABC-3", 100), sweep, "round unchanged");
  clear(task("ABC-3", 100), sweep);

  assert.equal(sayingOn(task("ABC-3", 100)).has("sweep"), false);
});
