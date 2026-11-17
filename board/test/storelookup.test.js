import assert from "node:assert/strict";
import { test } from "node:test";
import * as store from "../core/store.js";

test("aTaskIsFoundByIdAlone whenAnotherTaskHasThatIdAsAlias", () => {
  store.set({
    tasks: JSON.parse(
      JSON.stringify([{ id: "ABC-42", alias: "ABC-43" }, { id: "ABC-43" }]),
    ),
  });

  assert.equal(store.taskById("ABC-43")?.id, "ABC-43");
  assert.equal(store.taskById("ABC-99"), undefined);
});

test("theFirstAliaslessTaskIsFound whenTheNameIsEmpty", () => {
  store.set({
    tasks: JSON.parse(
      JSON.stringify([{ id: "ABC-42", alias: "login" }, { id: "ABC-43" }]),
    ),
  });

  assert.equal(store.taskFor("")?.id, "ABC-43");
});

test("noVerbIsOffered whenItFeedsAnotherTab", () => {
  store.set(
    JSON.parse(
      JSON.stringify({
        verbs: [{ id: "ship", part: "review" }],
        offers: { "ABC-42": ["ship"] },
      }),
    ),
  );

  assert.equal(
    store.offerFor(/** @type {never} */ ({ id: "ABC-42" }), "plan"),
    undefined,
  );
});
