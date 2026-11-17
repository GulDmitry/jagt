import assert from "node:assert/strict";
import { test } from "node:test";
import * as store from "../core/store.js";

test("aTaskIsFoundById whenTheNameIsItsId", () => {
  store.set({
    tasks: JSON.parse(
      JSON.stringify([{ id: "ABC-42" }, { id: "ABC-43", alias: "login" }]),
    ),
  });

  assert.equal(store.taskFor("ABC-43")?.id, "ABC-43");
});

test("aTaskIsFoundByAlias whenTheNameIsItsAlias", () => {
  store.set({
    tasks: JSON.parse(
      JSON.stringify([{ id: "ABC-42" }, { id: "ABC-43", alias: "login" }]),
    ),
  });

  assert.equal(store.taskFor("login")?.id, "ABC-43");
});

test("noTaskIsFound whenTheNameMatchesNothing", () => {
  store.set({ tasks: JSON.parse(JSON.stringify([{ id: "ABC-42" }])) });

  assert.equal(store.taskFor("missing"), undefined);
});

test("aTaskIsFoundOnlyByItsId whenAnotherTaskHasThatIdAsAlias", () => {
  store.set({
    tasks: JSON.parse(
      JSON.stringify([{ id: "ABC-42", alias: "ABC-43" }, { id: "ABC-43" }]),
    ),
  });

  assert.equal(store.taskById("ABC-43")?.alias, undefined);
});

test("bothNamesAreShown whenTheReferenceIsATaskWithAnAlias", () => {
  store.set({
    tasks: JSON.parse(JSON.stringify([{ id: "ABC-43", alias: "login" }])),
  });

  assert.equal(store.nameOf("login"), "login · ABC-43");
});

test("theReferenceIsKept whenNoTaskHasThatName", () => {
  store.set({ tasks: JSON.parse(JSON.stringify([{ id: "ABC-42" }])) });

  assert.equal(store.nameOf("gone"), "gone");
});

test("theEmptyReferenceNamesNoTask whenATaskWithoutAnAliasExists", () => {
  store.set({ tasks: JSON.parse(JSON.stringify([{ id: "ABC-42" }])) });

  assert.equal(store.nameOf(""), "");
});

test("offersAreThoseOfTheTask whenTheServerListedThem", () => {
  store.set({ offers: { "ABC-42": ["ship"] } });

  assert.deepEqual(store.offersOn(JSON.parse('{"id":"ABC-42"}')), ["ship"]);
});

test("noOffersExist whenTheServerListedNoneForTheTask", () => {
  store.set({ offers: {} });

  assert.deepEqual(store.offersOn(JSON.parse('{"id":"ABC-42"}')), []);
});

test("theOfferedVerbOfATabIsReturned whenTheTaskOffersIt", () => {
  store.set({
    offers: { "ABC-42": ["ship"] },
    verbs: [
      { id: "close", part: "review" },
      { id: "ship", part: "review" },
    ],
  });

  assert.equal(
    store.offerFor(JSON.parse('{"id":"ABC-42"}'), "review")?.id,
    "ship",
  );
});

test("noVerbIsOffered whenTheTaskDoesNotOfferThatTabsVerb", () => {
  store.set({
    offers: { "ABC-42": [] },
    verbs: [{ id: "ship", part: "review" }],
  });

  assert.equal(
    store.offerFor(JSON.parse('{"id":"ABC-42"}'), "review"),
    undefined,
  );
});

test("onlyTheGivenKeysChange whenTheStoreIsUpdated", () => {
  store.set({ projects: ["alpha"], masterMode: "off" });
  store.set({ masterMode: "act" });

  assert.deepEqual(store.projects(), ["alpha"]);
  assert.equal(store.masterMode(), "act");
});
