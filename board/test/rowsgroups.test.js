import assert from "node:assert/strict";
import { test } from "node:test";
import { grouped, manyProjects, rowClass } from "../components/rows.js";

/** @param {object} fields */
const task = (fields) => /** @type {never} */ (fields);

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

test("theTaskIsMarkedFlagged whenTheFlagHoldsIt", () => {
  const tasks = [{ id: "1", attention: "OPTIONAL", project: "a" }];

  const groups = grouped(/** @type {never} */ (tasks), false, () => true);

  assert.equal(groups[0].tasks[0].flagged, true);
});

test("theTaskIsNotMarked whenTheFlagIsCleared", () => {
  const tasks = [{ id: "1", attention: "OPTIONAL", project: "a" }];

  const groups = grouped(/** @type {never} */ (tasks), false, () => false);

  assert.equal(groups[0].tasks[0].flagged, undefined);
});

test("theRowIsFlagged whenTheTaskIsFlagged", () => {
  const row = rowClass(
    task({ owner: "HUMAN", attention: "REQUIRED", flagged: true }),
    false,
  );

  assert.equal(row, "row human flagged");
});

test("noGroupIsShown whenNothingIsShown", () => {
  assert.deepEqual(grouped([], false), []);
});
