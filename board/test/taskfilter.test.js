import assert from "node:assert/strict";
import { test } from "node:test";
import { byPhases, byText } from "../ui/taskfilter.js";

const TASKS = /** @type {never[]} */ ([
  { id: "ABC-42", alias: "p1", title: "Fix Login", phase: "build" },
  { id: "ABC-43", title: "Docs", phase: "review" },
]);

test("textMatchesAliasIdOrTitle whenTheNeedleIsTypedInAnyCase", () => {
  assert.deepEqual(
    byText(TASKS, " LOGIN ").map((task) => task.id),
    ["ABC-42"],
  );
  assert.deepEqual(
    byText(TASKS, "abc-43").map((task) => task.id),
    ["ABC-43"],
  );
});

test("everyTaskStays whenTheTextIsBlank", () => {
  assert.equal(byText(TASKS, "   ").length, 2);
});

test("tasksOfAnySelectedPhaseStay whenSeveralPhasesAreChosen", () => {
  assert.equal(byPhases(TASKS, new Set(["build", "review"])).length, 2);
  assert.deepEqual(
    byPhases(TASKS, new Set(["review"])).map(
      (task) => /** @type {{ id: string }} */ (task).id,
    ),
    ["ABC-43"],
  );
});

test("everyTaskStays whenNoPhaseIsChosen", () => {
  assert.equal(byPhases(TASKS, new Set()).length, 2);
});
