import assert from "node:assert/strict";
import { test } from "node:test";
import * as store from "../core/store.js";
import { hold } from "../ui/inflight.js";
import { blockedOn, offersOn } from "../ui/offers.js";

const task = /** @type {never} */ ({
  id: "ABC-42",
  actions: [{ id: "ship" }, { id: "stop" }],
});

test("onlyKnownVerbsAreOffered whenTheTaskHasOffersForUnknownOnes", () => {
  store.set({
    offers: { "ABC-42": ["replies", "gone"] },
    verbs: JSON.parse(
      JSON.stringify([{ id: "replies", hint: "read", part: "round" }]),
    ),
  });

  assert.deepEqual(offersOn(task), [
    { id: "replies", hint: "read", part: "round" },
  ]);
});

test("everyWritingVerbIsBlocked whenOneWritingPressIsInFlight", () => {
  hold(task, /** @type {never} */ ({ id: "ship" }));

  assert.deepEqual([...blockedOn(task)], ["ship", "stop"]);
});

test("noVerbIsBlocked whenTheTaskOffersNoActions", () => {
  const bare = /** @type {never} */ ({ id: "ABC-43" });

  assert.equal(blockedOn(bare).size, 0);
});
