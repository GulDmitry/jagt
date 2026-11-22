import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { test } from "node:test";

/** @typedef {{ lines: number, branches: number }} Floor */
/** @typedef {{ lines: { pct: number }, branches: { pct: number } }} Measured */

const here = new URL(".", import.meta.url);
/** @type {Record<string, Floor>} */
const floors = JSON.parse(readFileSync(new URL("floors.json", here), "utf8"));
/** @type {Record<string, Measured>} */
const summary = JSON.parse(
  readFileSync(
    new URL("../coverage/browser-report/coverage-summary.json", here),
    "utf8",
  ),
);
const boardRoot = new URL("..", here).pathname;
const measuredByFile = new Map(
  Object.entries(summary)
    .filter(([path]) => path.startsWith(boardRoot))
    .map(([path, measured]) => [path.slice(boardRoot.length), measured]),
);

for (const [file, floor] of Object.entries(floors)) {
  test(`${file} keeps its browser coverage floor`, () => {
    const measured = measuredByFile.get(file);
    assert.ok(measured, `${file} is missing from the browser coverage report`);
    assert.ok(
      measured.lines.pct >= floor.lines,
      `${file} lines ${measured.lines.pct}% under its floor ${floor.lines}%`,
    );
    assert.ok(
      measured.branches.pct >= floor.branches,
      `${file} branches ${measured.branches.pct}% under its floor ${floor.branches}%`,
    );
  });
}

test("every ui and components file has a floor", () => {
  const unfloored = [...measuredByFile.keys()].filter(
    (file) => !(file in floors),
  );
  assert.deepEqual(unfloored, []);
});
