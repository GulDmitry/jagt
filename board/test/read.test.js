import assert from "node:assert/strict";
import { mock, test } from "node:test";
import { changes, file, search, tree } from "../viewer/read.js";

const serve = (/** @type {object} */ body) =>
  mock.method(
    globalThis,
    "fetch",
    async () => new Response(JSON.stringify(body)),
  );

test("theTreeIsReadFromItsUrl whenATaskIsGiven", async () => {
  const fetched = serve([{ project: "alpha", files: ["a.txt"] }]);

  const read = await tree("ABC-42");

  assert.equal(
    fetched.mock.calls[0].arguments[0],
    "/api/tasks/code/tree?task=ABC-42",
  );
  assert.deepEqual(read, [{ project: "alpha", files: ["a.txt"] }]);
  mock.restoreAll();
});

test("eachRepoCarriesItsChangedFiles whenTheDiffIsRead", async () => {
  serve([
    {
      project: "alpha",
      cut: true,
      diff: "diff --git a/x.txt b/x.txt\n@@ -1 +1 @@\n-a\n+b\n",
    },
  ]);

  const repos = await changes("ABC-42");

  assert.equal(repos[0].project, "alpha");
  assert.equal(repos[0].cut, true);
  assert.equal(repos[0].files[0].path, "x.txt");
  mock.restoreAll();
});

test("thePathIsEncoded whenAFileIsRead", async () => {
  const fetched = serve({ kind: "text", path: "a b.txt", text: "" });

  await file("ABC-42", { project: "alpha", path: "a b.txt" });

  assert.equal(
    fetched.mock.calls[0].arguments[0],
    "/api/tasks/code/file?task=ABC-42&project=alpha&path=a%20b.txt",
  );
  mock.restoreAll();
});

test("theQueryIsEncoded whenTheCodeIsSearched", async () => {
  const fetched = serve({ hits: [] });

  await search("ABC-42", "alpha", "a&b");

  assert.equal(
    fetched.mock.calls[0].arguments[0],
    "/api/tasks/code/search?task=ABC-42&project=alpha&q=a%26b",
  );
  mock.restoreAll();
});
