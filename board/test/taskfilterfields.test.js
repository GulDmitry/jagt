import assert from "node:assert/strict";
import { test } from "node:test";
import { byText } from "../ui/taskfilter.js";

test("aMissingAliasMatchesNothing whenTheNeedleIsText", () => {
  const tasks = /** @type {never[]} */ ([{ id: "ABC-43", title: "Docs" }]);

  assert.deepEqual(byText(tasks, "here"), []);
});
