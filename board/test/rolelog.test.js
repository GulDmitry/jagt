import assert from "node:assert/strict";
import { mock, test } from "node:test";
import { close, follow, lines, onChange, open, toggle } from "../ui/rolelog.js";

const serve = (/** @type {object} */ body) =>
  mock.method(
    globalThis,
    "fetch",
    async () => new Response(JSON.stringify(body)),
  );

test("theLogOpensAndReads whenARoleIsToggled", async () => {
  const fetched = serve({ lines: ["one"], next: 1 });
  fetched.mock.mockImplementationOnce(
    async () => new Response(JSON.stringify({ lines: ["two"], next: 2 })),
    1,
  );
  onChange(() => {});

  toggle("master");
  await follow("master");

  assert.equal(open(), "master");
  assert.deepEqual(lines(), ["one", "two"]);
  mock.restoreAll();
  close();
});

test("theLogCloses whenTheOpenRoleIsToggledAgain", async () => {
  serve({ lines: [], next: 0 });
  onChange(() => {});
  toggle("master");

  toggle("master");

  assert.equal(open(), null);
  mock.restoreAll();
});

test("nothingIsRead whenTheRoleIsNotTheOpenOne", async () => {
  const fetched = serve({ lines: ["x"], next: 1 });
  close();

  await follow("master");

  assert.equal(fetched.mock.calls.length, 0);
  mock.restoreAll();
});

test("theLogKeepsItsLines whenTheReadFails", async () => {
  mock.method(globalThis, "fetch", async () => {
    throw new Error("down");
  });
  onChange(() => {});
  toggle("master");

  await follow("master");

  assert.deepEqual(lines(), []);
  mock.restoreAll();
  close();
});
