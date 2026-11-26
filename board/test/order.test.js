import assert from "node:assert/strict";
import { test } from "node:test";
import { holds, sorted, toggle } from "../ui/order.js";

const tasks = /** @type {never[]} */ ([{ alias: "p2" }, { alias: "p1" }]);

test("tasksKeepTheirOrder whenAliasOrderIsOff", () => {
  assert.equal(holds(), false);
  assert.deepEqual(sorted(tasks), [{ alias: "p2" }, { alias: "p1" }]);
});

test("tasksSortByAlias whenAliasOrderIsToggledOn", () => {
  toggle();

  assert.equal(holds(), true);
  assert.deepEqual(sorted(tasks), [{ alias: "p1" }, { alias: "p2" }]);
});
