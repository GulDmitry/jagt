import assert from "node:assert/strict";
import { readdirSync, readFileSync } from "node:fs";
import { test } from "node:test";

/** @param {string} name @returns {Record<string, string>} */
const catalog = (name) =>
  JSON.parse(
    readFileSync(new URL(`../i18n/${name}.json`, import.meta.url), "utf8"),
  );

/** @type {string[]} */
const languages = JSON.parse(
  readFileSync(new URL("../i18n/index.json", import.meta.url), "utf8"),
);

/** @param {string} text */
const holes = (text) =>
  [...text.matchAll(/\{(\w+)\}/g)]
    .map((m) => m[1])
    .sort()
    .join(",");

test("aTranslationNamesEnglishHoles whenItTranslatesAKey", () => {
  const english = catalog("en");

  const differing = languages.flatMap((language) =>
    Object.entries(catalog(language))
      .filter(
        ([key, text]) => key in english && holes(text) !== holes(english[key]),
      )
      .map(([key]) => `${language}: ${key}`),
  );

  assert.deepEqual(differing, []);
});

test("aCatalogHoldsOnlyEnglishKeys whenItTranslates", () => {
  const english = catalog("en");

  const unknown = languages.flatMap((language) =>
    Object.keys(catalog(language))
      .filter((key) => !(key in english))
      .map((key) => `${language}: ${key}`),
  );

  assert.deepEqual(unknown, []);
});

const SKIPPED = [
  "vendor",
  "node_modules",
  "test",
  "i18n",
  "coverage",
  "coverage-floors",
];

/** @param {string} dir @returns {string[]} */
const sourcesIn = (dir) =>
  readdirSync(new URL(dir, import.meta.url), { withFileTypes: true }).flatMap(
    (entry) => {
      if (entry.isDirectory())
        return SKIPPED.includes(entry.name)
          ? []
          : sourcesIn(`${dir}${entry.name}/`);
      return /\.(m?js|html)$/.test(entry.name) ? [`${dir}${entry.name}`] : [];
    },
  );

const sources = () =>
  sourcesIn("../").map((path) => ({
    path,
    text: readFileSync(new URL(path, import.meta.url), "utf8"),
  }));

/** @param {string} pattern @param {string} text */
const firsts = (pattern, text) =>
  [...text.matchAll(new RegExp(pattern, "g"))].map((m) => m[1]);

const ROW = '\\brow\\(\\s*"(\\w+)"';

test("everyAskedKeyExists whenABoardSourceCallsTOrNamesAnAttribute", () => {
  const english = catalog("en");

  const missing = sources().flatMap(({ path, text }) =>
    [
      ...firsts('\\bt\\(\\s*"([^"]+)"', text),
      ...firsts('\\bt\\([^"`)]*\\?\\s*"([^"]+)"\\s*:\\s*"[^"]+"', text),
      ...firsts('\\bt\\([^"`)]*\\?\\s*"[^"]+"\\s*:\\s*"([^"]+)"', text),
      ...firsts('data-t[\\w-]*="([^"${]+)"', text),
      ...firsts(ROW, text).map((row) => `legend.row.${row}`),
    ]
      .filter((key) => !(key in english))
      .map((key) => `${path}: ${key}`),
  );

  assert.deepEqual(missing, []);
});

test("everyEnglishKeyIsAsked whenTheCatalogHoldsIt", () => {
  const text = sources()
    .map((source) => source.text)
    .join("\n");
  const patterns = firsts("\\bt\\(`([^`]*)`", text)
    .filter((template) => !template.startsWith("legend.row."))
    .map(
      (template) =>
        new RegExp(
          `^${template.replaceAll(".", "\\.").replace(/\$\{[^}]*\}/g, ".+")}$`,
        ),
    );
  const rows = firsts(ROW, text).map((row) => `legend.row.${row}`);

  const unasked = Object.keys(catalog("en")).filter(
    (key) =>
      !text.includes(`"${key}"`) &&
      !rows.includes(key) &&
      !patterns.some((pattern) => pattern.test(key)),
  );

  assert.deepEqual(unasked, []);
});

test("noEnglishWordsAreHardcoded whenABoardSourceBuildsATemplateLiteral", () => {
  const stylesheet = readFileSync(
    new URL("../app.css", import.meta.url),
    "utf8",
  );

  const hardcoded = sources().flatMap(({ path, text }) =>
    firsts("`([A-Za-z]{3,}[ ][^`]*)`", text)
      .filter((template) => template.includes("${"))
      .filter((template) => {
        const first = template.split(" ")[0];
        return first !== "jagt" && !stylesheet.includes(`.${first}`);
      })
      .map((template) => `${path}: ${template}`),
  );

  assert.deepEqual(hardcoded, []);
});
