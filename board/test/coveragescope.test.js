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

const BROWSER_GLOBAL =
  /\b(document|window|localStorage|sessionStorage|navigator|location|EventSource|WebSocket|MutationObserver|ResizeObserver|IntersectionObserver|requestAnimationFrame|matchMedia|customElements|Element|HTMLElement|HTMLInputElement|HTMLFormElement|HTMLDialogElement|HTMLButtonElement|HTMLSelectElement|ParentNode|Event|KeyboardEvent|MouseEvent|CustomEvent|MessageEvent|FormData|Node|DOMParser|getComputedStyle|alert|confirm)\b/;

const importsOf = (/** @type {string} */ file) =>
  [
    ...readFileSync(new URL(file, root), "utf8").matchAll(
      /(?:from\s+|import\s*\(?\s*)"(\.{1,2}\/[^"]+)"/g,
    ),
  ].map(([, path]) =>
    new URL(path, new URL(file, root)).pathname.slice(root.pathname.length),
  );

const RENDERER = /from\s+"(preact|htm)"/;

test("noExcusedFileReachesNeitherABrowserGlobalNorAnExcusedFile", () => {
  const needless = Object.keys(rc.excludeReasons).filter(
    (file) =>
      sources.includes(file) &&
      !BROWSER_GLOBAL.test(readFileSync(new URL(file, root), "utf8")) &&
      !RENDERER.test(readFileSync(new URL(file, root), "utf8")) &&
      !importsOf(file).some((path) => path in rc.excludeReasons),
  );

  assert.deepEqual(needless, []);
});
