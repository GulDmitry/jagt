import assert from "node:assert/strict";
import { test } from "node:test";
import { blocked, hold, release, waitingFor } from "../ui/inflight.js";

const task = /** @type {never} */ ({ id: "ABC-42" });
const write = /** @type {never} */ ({ id: "ship" });

test("theVerbIsBlockedAndNamed whenItsPressIsHeld", () => {
  hold(task, write);

  assert.equal(blocked(task, write), true);
  assert.equal(waitingFor(task, write), "ship");
});

test("theVerbIsFree whenItsPressIsReleased", () => {
  hold(task, write);
  release(task, write);

  assert.equal(blocked(task, write), false);
});
