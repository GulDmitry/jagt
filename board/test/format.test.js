import assert from "node:assert/strict";
import { test } from "node:test";
import { serveFiles } from "./fakefetch.js";

const FILES = { "i18n/en.json": { "stop.at": "stopped {at}" } };
serveFiles(FILES);
const { bothNames, countdown, duration } = await import("../core/format.js");

/** @type {[number, string][]} */
const SPANS = [
  [0, "0m"],
  [-5000, "0m"],
  [59999, "0m"],
  [60000, "1m"],
  [3540000, "59m"],
  [3600000, "1h"],
  [86340000, "23h"],
  [86400000, "1d"],
  [172800000, "2d"],
];

SPANS.forEach(([millis, shown]) =>
  test(`durationReads ${shown} whenTheSpanIs${millis}Millis`, () => {
    assert.equal(duration(millis), shown);
  }),
);

/** @type {[number, string][]} */
const WAITS = [
  [0, "0s"],
  [-1000, "0s"],
  [59999, "59s"],
  [60000, "1m"],
  [61000, "2m"],
  [600000, "10m"],
  [3540000, "59m"],
  [3600000, "1h"],
  [3660000, "2h"],
];

WAITS.forEach(([millis, shown]) =>
  test(`countdownReads ${shown} whenTheWaitIs${millis}Millis`, () => {
    assert.equal(countdown(millis), shown);
  }),
);

test("bothNamesJoinAliasAndId whenTheTaskHasAnAlias", () => {
  assert.equal(bothNames({ id: "ABC-42", alias: "login" }), "login · ABC-42");
});

test("onlyTheIdIsShown whenTheTaskHasNoAlias", () => {
  assert.equal(bothNames({ id: "ABC-42" }), "ABC-42");
});
