import assert from "node:assert/strict";
import { mock, test } from "node:test";
import { sayTo } from "../core/api.js";

test("theBodyIsSentAsJson whenALineIsSaid", async () => {
  const fetched = mock.method(
    globalThis,
    "fetch",
    async () => new Response("{}"),
  );

  await sayTo("a", "go");

  assert.deepEqual(fetched.mock.calls[0].arguments[1]?.headers, {
    "Content-Type": "application/json",
  });
  mock.restoreAll();
});
