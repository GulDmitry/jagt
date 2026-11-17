import assert from "node:assert/strict";
import { test } from "node:test";
import { dockOf } from "../components/panefacts.js";

const card = (/** @type {object} */ task) => /** @type {never} */ ({ task });

test("theDockIsOff whenTheNamedWindowIsNotHeld", () => {
  const dock = dockOf(
    "session",
    card({ owner: "HUMAN", actions: [] }),
    { held: false },
    {},
  );

  assert.equal(dock.off, true);
});

test("theDockSendsToTheNamedWindow whenAWindowIsChosen", () => {
  const dock = dockOf(
    "session",
    card({ owner: "HUMAN", actions: [{ id: "run" }] }),
    { name: "w1", held: false },
    {},
  );

  assert.deepEqual([dock.named, dock.off, dock.canRun], ["w1", false, false]);
});

test("theRunButtonShows whenNoWindowIsNamedAndTheTaskRuns", () => {
  const dock = dockOf(
    "session",
    card({ owner: "HUMAN", actions: [{ id: "run" }] }),
    { held: true },
    {},
  );

  assert.equal(dock.canRun, true);
});

test("theDockIsOff whenThePeekIsNotHeldOutsideTheSession", () => {
  const dock = dockOf("overview", card({ owner: "HUMAN" }), null, {
    held: false,
  });

  assert.deepEqual([dock.off, dock.named], [true, null]);
});

test("thePeekLineShows whenAnAgentOwnsTheTaskOutsideTheSession", () => {
  const dock = dockOf("overview", card({ owner: "AGENT" }), null, {
    held: true,
    line: "working",
  });

  assert.equal(dock.line, "working");
});

test("noPeekLineShows whenTheHumanOwnsTheTask", () => {
  const dock = dockOf("overview", card({ owner: "HUMAN" }), null, {
    held: true,
    line: "working",
  });

  assert.equal(dock.line, "");
});

test("theRunButtonIsHidden whenTheTaskHasOnlyOtherVerbs", () => {
  const dock = dockOf(
    "session",
    card({ owner: "HUMAN", actions: [{ id: "ship" }, { id: "stop" }] }),
    { held: true },
    {},
  );

  assert.equal(dock.canRun, false);
});

test("theRunButtonShows whenAnotherVerbComesFirst", () => {
  const dock = dockOf(
    "session",
    card({ owner: "HUMAN", actions: [{ id: "ship" }, { id: "run" }] }),
    { held: true },
    {},
  );

  assert.equal(dock.canRun, true);
});

test("theDockWaitsOpen whenTheSessionIsStillReading", () => {
  const dock = dockOf("session", card({ owner: "HUMAN" }), null, {});

  assert.equal(dock.off, false);
});
