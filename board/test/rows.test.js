import assert from "node:assert/strict";
import { test } from "node:test";
import {
  grouped,
  manyProjects,
  pickLabel,
  quickClass,
  rowClass,
  stoppedLine,
} from "../components/rows.js";

/** @param {object} fields */
const task = (fields) => /** @type {never} */ (fields);

test("theQuickVerbIsPrimary whenTheActionIs", () => {
  assert.deepEqual(
    [{ primary: true }, {}].map((action) => quickClass(task(action))),
    ["row-verb primary", "row-verb"],
  );
});

test("theRowNamesItsOwnerAndState whenOptionalAndSelected", () => {
  const row = rowClass(task({ owner: "AGENT", attention: "OPTIONAL" }), true);

  assert.equal(row, "row agent optional selected");
});

test("theRowIsPlain whenRequiredAndNotSelected", () => {
  const row = rowClass(task({ owner: "HUMAN", attention: "REQUIRED" }), false);

  assert.equal(row, "row human");
});

test("noLineIsStopped whenTheWatchRunsOnSchedule", () => {
  const watching = {
    autoReview: { state: "WATCHING", note: "n", nextPollAt: 10 },
  };

  assert.equal(stoppedLine(task(watching), 0), null);
});

test("theStopIsSaid whenTheWatchStalled", () => {
  const stalled = {
    autoReview: { state: "STOPPED", label: "Review", note: "no answer" },
  };

  assert.equal(stoppedLine(task(stalled), 0), "Review — no answer");
});

test("theLabelListsEveryFact whenChangedAndChecked", () => {
  const label = pickLabel(
    task({
      alias: "a1",
      id: "ABC-42",
      title: "Fix",
      statusLabel: "In work",
      pipeline: "RED",
      pipelineSaid: "tests fail",
    }),
    true,
    "stopped",
  );

  assert.equal(
    label,
    "a1 Fix, In work, checks: tests fail, stopped, list.changed",
  );
});

test("theChecksAreLowercased whenThePipelineSaidNothing", () => {
  const label = pickLabel(
    task({ id: "ABC-42", statusLabel: "Done", pipeline: "RED" }),
    false,
    null,
  );

  assert.equal(label, "ABC-42 , Done, checks: red");
});

test("theLabelFallsBackToTheId whenThereIsNoAlias", () => {
  const label = pickLabel(
    task({ id: "ABC-42", statusLabel: "Done", pipeline: "NONE" }),
    false,
    null,
  );

  assert.equal(label, "ABC-42 , Done");
});

test("noLineIsStopped whenTheTaskHasNoWatch", () => {
  assert.equal(stoppedLine(task({}), 0), null);
});

test("projectsAreNotMany whenATaskHasNoRepoBesideOne", () => {
  const tasks = [{ repos: [{ project: "a" }] }, {}];

  assert.equal(manyProjects(/** @type {never} */ (tasks)), false);
});

test("projectsAreMany whenTheReposSpanTwoProjects", () => {
  const tasks = [
    { repos: [{ project: "a" }] },
    { repos: [{ project: "b" }] },
    {},
  ];

  assert.equal(manyProjects(/** @type {never} */ (tasks)), true);
});

test("projectsAreNotMany whenOneProjectRepeats", () => {
  const tasks = [{ repos: [{ project: "a" }] }, { repos: [{ project: "a" }] }];

  assert.equal(manyProjects(/** @type {never} */ (tasks)), false);
});

test("yoursComeFirstThenTheRest whenOneProject", () => {
  const tasks = [
    { id: "1", attention: "OPTIONAL", project: "b" },
    { id: "2", attention: "REQUIRED", project: "a" },
  ];

  const groups = grouped(/** @type {never} */ (tasks), false);

  assert.deepEqual(
    groups.map((group) => [group.name, group.tasks.map((one) => one.id)]),
    [
      ["list.yours", ["2"]],
      ["list.rest", ["1"]],
    ],
  );
});

test("theRestIsUnnamed whenNothingIsYours", () => {
  const tasks = [{ id: "1", attention: "OPTIONAL", project: "b" }];

  const groups = grouped(/** @type {never} */ (tasks), false);

  assert.deepEqual(groups, [{ name: "", kind: "project", tasks: tasks }]);
});

test("theRestSplitsByProjectName whenProjectsAreMany", () => {
  const tasks = [
    { id: "1", attention: "OPTIONAL", project: "b" },
    { id: "2", attention: "OPTIONAL", project: "a" },
    { id: "3", attention: "REQUIRED", project: "b" },
  ];

  const groups = grouped(/** @type {never} */ (tasks), true);

  assert.deepEqual(
    groups.map((group) => [
      group.name,
      group.kind,
      group.tasks.map((one) => one.id),
    ]),
    [
      ["list.yours", "yours", ["3"]],
      ["a", "project", ["2"]],
      ["b", "project", ["1"]],
    ],
  );
});

test("aFlaggedRowLeadsItsGroup whenTheFlagHoldsIt", () => {
  const tasks = [
    { id: "1", attention: "OPTIONAL", project: "a" },
    { id: "2", attention: "OPTIONAL", project: "a" },
  ];

  const groups = grouped(
    /** @type {never} */ (tasks),
    false,
    (/** @type {{ id: string }} */ one) => one.id === "2",
  );

  assert.deepEqual(
    groups[0].tasks.map((one) => one.id),
    ["2", "1"],
  );
});

test("noGroupIsShown whenNothingIsShown", () => {
  assert.deepEqual(grouped([], false), []);
});
