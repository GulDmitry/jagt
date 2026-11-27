import assert from "node:assert/strict";
import { test } from "node:test";
import { readThenPaint } from "../ui/refreshread.js";

test("theBackendMessageStays whenTheReadFails", async () => {
  const failure = await readThenPaint(
    () => Promise.reject(new Error("connection refused")),
    () => assert.fail("nothing to paint"),
  );

  assert.deepEqual(failure, {
    key: "refresh.backend",
    cause: "connection refused",
  });
});

test("theRenderFailureIsNamed whenPaintingThrows", async () => {
  const failure = await readThenPaint(
    () => Promise.resolve({ tasks: [] }),
    () => {
      throw new TypeError("Cannot read properties of null");
    },
  );

  assert.deepEqual(failure, {
    key: "refresh.render",
    cause: "Cannot read properties of null",
  });
});

test("nothingIsReported whenReadAndPaintSucceed", async () => {
  /** @type {unknown[]} */
  const painted = [];

  const failure = await readThenPaint(
    () => Promise.resolve({ tasks: ["ABC-42"] }),
    (data) => painted.push(data),
  );

  assert.equal(failure, null);
  assert.deepEqual(painted, [{ tasks: ["ABC-42"] }]);
});
