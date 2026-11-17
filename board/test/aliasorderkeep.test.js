import assert from "node:assert/strict";
import { test } from "node:test";
import { byAlias } from "../ui/aliasorder.js";

test("aliasesAreAscending whenTheyArriveDescending", () => {
  const tasks = [
    { id: "c", alias: "p3" },
    { id: "a", alias: "p1" },
    { id: "b", alias: "p2" },
  ];

  assert.deepEqual(
    byAlias(/** @type {never} */ (tasks)).map((task) => task.id),
    ["a", "b", "c"],
  );
});

test("aliaslessTasksKeepTheirOrder whenNoneHasAnAlias", () => {
  const tasks = [{ id: "x" }, { id: "y" }, { id: "z" }];

  assert.deepEqual(
    byAlias(/** @type {never} */ (tasks)).map((task) => task.id),
    ["x", "y", "z"],
  );
});
