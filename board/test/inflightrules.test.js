import assert from "node:assert/strict";
import { test } from "node:test";
import {
  blocked,
  create,
  hold,
  release,
  waitingFor,
} from "../ui/inflightrules.js";

const task = /** @type {never} */ ({ id: "ABC-42" });
const write = /** @type {never} */ ({ id: "ship" });
const other = /** @type {never} */ ({ id: "stop" });
const read = /** @type {never} */ ({ id: "peek", readOnly: true });

test("theSameButtonIsBlocked whenItsPressIsInFlight", () => {
  const state = create();
  hold(state, task, read);

  assert.equal(blocked(state, task, read), true);
});

test("anotherWritingButtonIsBlocked whenAWriteIsInFlight", () => {
  const state = create();
  hold(state, task, write);

  assert.equal(blocked(state, task, other), true);
});

test("aReadingButtonIsFree whenAWriteIsInFlight", () => {
  const state = create();
  hold(state, task, write);

  assert.equal(blocked(state, task, read), false);
});

test("theWaitNamesTheWriter whenAWriteIsInFlight", () => {
  const state = create();
  hold(state, task, write);

  assert.equal(waitingFor(state, task, other), "ship");
});

test("theWaitNamesTheButton whenNothingWrites", () => {
  assert.equal(waitingFor(create(), task, other), "stop");
});

test("nothingIsBlocked whenThePressIsReleased", () => {
  const state = create();
  hold(state, task, write);
  release(state, task, write);

  assert.equal(blocked(state, task, write), false);
});

test("theWriterStays whenAReadIsReleased", () => {
  const state = create();
  hold(state, task, write);
  hold(state, task, read);
  release(state, task, read);

  assert.equal(blocked(state, task, other), true);
});

test("aWriteIsFree whenOnlyAReadIsInFlight", () => {
  const state = create();
  hold(state, task, read);

  assert.equal(blocked(state, task, write), false);
});
