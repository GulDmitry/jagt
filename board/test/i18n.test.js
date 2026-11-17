import assert from "node:assert/strict";
import { afterEach, mock, test } from "node:test";

/** @type {Record<string, unknown>} */
const FILES = {
  "i18n/en.json": { hello: "Hello {name}", only: "English only" },
  "i18n/de.json": { hello: "Hallo {name}", "language.name": "Deutsch" },
  "i18n/index.json": ["en", "de"],
  "user/i18n/en.json": { only: "Overridden" },
  "user/i18n/index.json": ["fr"],
  "user/i18n/fr.json": {},
};

/** @param {Record<string, unknown>} files */
const serve = (files) =>
  mock.method(globalThis, "fetch", async (/** @type {string} */ url) =>
    url in files
      ? new Response(JSON.stringify(files[url]))
      : new Response("", { status: 404 }),
  );

const loads = { count: 0 };
/** @returns {Promise<typeof import("../core/i18n.js")>} */
const fresh = () => import(`../core/i18n.js?load=${++loads.count}`);

afterEach(() => {
  mock.restoreAll();
  Reflect.deleteProperty(globalThis, "localStorage");
});

test("aKeyIsFilledWithItsValues whenTheTextHasHoles", async () => {
  serve(FILES);
  const { t } = await fresh();

  assert.equal(t("hello", { name: "Ada" }), "Hello Ada");
});

test("aHoleStaysAsWritten whenNoValueIsGiven", async () => {
  serve(FILES);
  const { t } = await fresh();

  assert.equal(t("hello"), "Hello {name}");
});

test("theKeyIsShown whenNoLanguageKnowsIt", async () => {
  serve(FILES);
  const { t } = await fresh();

  assert.equal(t("missing.key"), "missing.key");
});

test("theUserFileWins whenBothDefineAKey", async () => {
  serve(FILES);
  const { t } = await fresh();

  assert.equal(t("only"), "Overridden");
});

test("theChosenLanguageIsUsed whenItWasStored", async () => {
  serve(FILES);
  Object.assign(globalThis, { localStorage: { lang: "de" } });
  const { t, current } = await fresh();

  assert.deepEqual(
    [current(), t("hello", { name: "Ada" })],
    ["de", "Hallo Ada"],
  );
});

test("englishFillsTheGap whenTheChosenLanguageLacksAKey", async () => {
  serve(FILES);
  Object.assign(globalThis, { localStorage: { lang: "de" } });
  const { t } = await fresh();

  assert.equal(t("only"), "Overridden");
});

test("englishIsUsed whenStorageIsUnreadable", async () => {
  serve(FILES);
  const { current } = await fresh();

  assert.equal(current(), "en");
});

test("theChoiceIsStored whenALanguageIsChosen", async () => {
  serve(FILES);
  const storage = { lang: "" };
  Object.assign(globalThis, { localStorage: storage });
  const { choose } = await fresh();

  choose("de");

  assert.equal(storage.lang, "de");
});

test("choosingDoesNotThrow whenStorageIsUnavailable", async () => {
  serve(FILES);
  const { choose } = await fresh();

  assert.doesNotThrow(() => choose("de"));
});

test("languagesAreListedOnce whenBothFoldersNameThem", async () => {
  serve(FILES);
  const { languages } = await fresh();

  assert.deepEqual(await languages(), [
    { code: "de", name: "Deutsch" },
    { code: "en", name: "en" },
    { code: "fr", name: "fr" },
  ]);
});

test("noLanguageIsListed whenNoIndexIsServed", async () => {
  serve({ "i18n/en.json": {} });
  const { languages } = await fresh();

  assert.deepEqual(await languages(), []);
});
