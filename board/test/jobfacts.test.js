import assert from "node:assert/strict";
import { mock, test } from "node:test";

const FILES = {
  "i18n/en.json": {
    due: "due",
    "jobs.ago": "{when} ago",
    "jobs.in": "in {when}",
    "jobs.not-yet": "not yet",
    "jobs.failed": "failed",
    "jobs.running": "running",
    "jobs.fine": "fine",
    "jobs.never": "never",
    "jobs.not-again": "not again",
    "jobs.at-start": "at start",
  },
};
mock.method(globalThis, "fetch", async (/** @type {string} */ url) =>
  url in FILES
    ? new Response(
        JSON.stringify(FILES[/** @type {keyof typeof FILES} */ (url)]),
      )
    : new Response("", { status: 404 }),
);
const { ago, dot, next, tables, verdict } =
  await import("../components/jobfacts.js");

/** @param {object} fields */
const job = (fields) => /** @type {never} */ (fields);

test.describe("theDotFollowsTheLastRun", () => {
  for (const [
    name,
    fields,
    expected,
  ] of /** @type {[string, object, string][]} */ ([
    ["red whenItFailed", { lastError: "x", running: true }, "red"],
    ["running whenItRuns", { running: true, lastStartedAt: 1 }, "running"],
    ["green whenItRanFine", { lastStartedAt: 1 }, "green"],
    ["empty whenItNeverRan", {}, ""],
  ])) {
    test(name, () => assert.equal(dot(job(fields)), expected));
  }
});

test.describe("theVerdictFollowsTheLastRun", () => {
  for (const [
    name,
    fields,
    expected,
  ] of /** @type {[string, object, string][]} */ ([
    ["failed whenItFailed", { lastError: "x", running: true }, "failed"],
    ["running whenItRuns", { running: true }, "running"],
    ["fine whenItRanFine", { lastStartedAt: 1 }, "fine"],
    ["never whenItNeverRan", {}, "never"],
  ])) {
    test(name, () => assert.equal(verdict(job(fields)), expected));
  }
});

test("theLastRunIsNotYet whenThereIsNone", () => {
  assert.equal(ago(null, 100), "not yet");
});

test("theLastRunIsAgo whenThereIsOne", () => {
  assert.equal(ago(70000, 100000), "30s ago");
});

test.describe("theNextRunIsSaid", () => {
  for (const [
    name,
    fields,
    expected,
  ] of /** @type {[string, object, string][]} */ ([
    ["due whenItsTimeHasPassed", { nextRunAt: 50 }, "due"],
    ["due whenItsTimeIsNow", { nextRunAt: 100 }, "due"],
    ["in whenItIsAhead", { nextRunAt: 30100 }, "in 30s"],
    ["notAgain whenItRanAndHasNoNext", { lastStartedAt: 1 }, "not again"],
    ["atStart whenItNeverRan", {}, "at start"],
  ])) {
    test(name, () => assert.equal(next(job(fields), 100), expected));
  }
});

test("failedJobsComeFirstWithinTheirService whenTablesAreBuilt", () => {
  const jobs = [
    { id: "a", serves: "s1" },
    { id: "b", serves: "s2" },
    { id: "c", serves: "s1", lastError: "boom" },
  ];

  const built = tables(/** @type {never} */ (jobs));

  assert.deepEqual(
    built.map((table) => [table.group, table.jobs.map((one) => one.id)]),
    [
      ["s1", ["c", "a"]],
      ["s2", ["b"]],
    ],
  );
});

test("theNextRunCountsDown whenItIsAMinuteAhead", () => {
  assert.equal(next(job({ nextRunAt: 160000 }), 100000), "in 1m");
});
