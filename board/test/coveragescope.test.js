import assert from "node:assert/strict";
import { readdirSync, readFileSync } from "node:fs";
import { test } from "node:test";

const root = new URL("../", import.meta.url);

/** @type {{include: string[], excludeReasons: Record<string, string>}} */
const rc = JSON.parse(readFileSync(new URL(".c8rc.json", root), "utf8"));

const sources = readdirSync(root, { recursive: true, encoding: "utf8" })
  .filter((file) => /\.m?js$/.test(file))
  .filter(
    (file) =>
      !/^(node_modules|vendor|test|coverage|coverage-floors)\//.test(file),
  )
  .filter((file) => !/\.config\.m?js$/.test(file))
  .sort();

test("everyBoardSourceIsGatedOrExcludedWithItsReason", () => {
  const unaccounted = sources.filter(
    (file) => !rc.include.includes(file) && !rc.excludeReasons[file],
  );

  assert.deepEqual(unaccounted, []);
});

test("noExclusionIsStaleOrAlsoIncluded", () => {
  const stale = Object.keys(rc.excludeReasons).filter(
    (file) => !sources.includes(file) || rc.include.includes(file),
  );

  assert.deepEqual(stale, []);
});

test("everyIncludedFileExists", () => {
  const missing = rc.include.filter((file) => !sources.includes(file));

  assert.deepEqual(missing, []);
});
