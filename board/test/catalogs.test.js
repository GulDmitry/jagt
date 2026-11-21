import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
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
