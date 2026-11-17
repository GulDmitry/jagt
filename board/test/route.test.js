import assert from "node:assert/strict";
import { mock, test } from "node:test";

mock.method(globalThis, "fetch", async () => new Response("{}"));
const store = await import("../core/store.js");
const { routeLine } = await import("../ui/route.js");

store.set(
  /** @type {never} */ ({
    verbs: [
      { id: "ship", takesTask: true, hint: "" },
      { id: "go", takesTask: false, hint: "" },
    ],
    tasks: [{ id: "ABC-42", alias: "login" }],
  }),
);

test("verbAloneIsJudged whenItTakesATaskAndNamesNone", () => {
  assert.equal(routeLine("ship").route, "judge");
});

test("lineRunsDirectly whenTheVerbNeedsNoTask", () => {
  assert.equal(routeLine("go now").route, "run");
});

test("lineRunsDirectly whenTheArgumentNamesATask", () => {
  assert.equal(routeLine("ship login").route, "run");
});

test("lineGoesToTheModel whenTheArgumentNamesNoTask", () => {
  assert.equal(routeLine("ship some prose").route, "interpret");
});

test("lineGoesToTheModel whenNoVerbIsTyped", () => {
  assert.equal(routeLine("hello there").route, "interpret");
});
