import assert from "node:assert/strict";
import { mock, test } from "node:test";

mock.method(globalThis, "fetch", async () => new Response("", { status: 404 }));
const store = await import("../core/store.js");
const { parse } = await import("../core/grammar.js");

const VERBS = [
  { id: "ship", takesTask: true },
  { id: "go", takesTask: false },
  { id: "open", takesTask: true, takesProject: true },
];
const TASKS = [{ id: "ABC-42", alias: "login" }];

test("onlyVerbAndArgumentAreReturned whenTheVerbTakesNoTask", () => {
  store.set(JSON.parse(JSON.stringify({ verbs: VERBS, tasks: TASKS })));

  assert.deepEqual(parse("go login"), {
    verb: VERBS[1],
    argument: "login",
  });
});

test("noProjectIsSplit whenTheWholeArgumentNamesATask", () => {
  store.set(JSON.parse(JSON.stringify({ verbs: VERBS, tasks: TASKS })));

  assert.deepEqual(parse("open login"), {
    verb: VERBS[2],
    argument: "login",
    task: TASKS[0],
  });
});

test("noProjectIsSplit whenTheVerbTakesNoProject", () => {
  store.set(JSON.parse(JSON.stringify({ verbs: VERBS, tasks: TASKS })));

  assert.deepEqual(parse("ship nothing"), {
    verb: VERBS[0],
    argument: "nothing",
    task: undefined,
  });
});
