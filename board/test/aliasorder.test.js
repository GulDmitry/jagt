import assert from "node:assert/strict";
import { test } from "node:test";
import { byAlias } from "../ui/aliasorder.js";

test("p2SortsBeforeP10 whenAliasesAreOrderedByNumber", () => {
  const tasks = [
    { id: "a", alias: "p10" },
    { id: "b", alias: "p2" },
  ];

  assert.deepEqual(
    byAlias(/** @type {never} */ (tasks)).map((task) => task.id),
    ["b", "a"],
  );
});

test("aliaslessTasksSortLast whenSomeTasksHaveNoAlias", () => {
  const tasks = [{ id: "a" }, { id: "b", alias: "p1" }];

  assert.deepEqual(
    byAlias(/** @type {never} */ (tasks)).map((task) => task.id),
    ["b", "a"],
  );
});

test("inputIsLeftUntouched whenTasksAreSortedByAlias", () => {
  const tasks = [
    { id: "a", alias: "p2" },
    { id: "b", alias: "p1" },
  ];

  byAlias(/** @type {never} */ (tasks));

  assert.deepEqual(
    tasks.map((task) => task.id),
    ["a", "b"],
  );
});

test("orderIsKept whenTwoTasksHaveNoAlias", () => {
  const tasks = [{ id: "a" }, { id: "b" }];

  assert.deepEqual(
    byAlias(/** @type {never} */ (tasks)).map((task) => task.id),
    ["a", "b"],
  );
});
