import assert from "node:assert/strict";
import { mock, test } from "node:test";

mock.method(globalThis, "fetch", async (/** @type {string} */ url) =>
  url === "i18n/en.json"
    ? new Response(
        JSON.stringify({
          "grammar.plain": "plain {word}",
          "grammar.no-task": "no task {argument}",
          "grammar.needs-task": "{verb} needs a task",
          "grammar.ok": "ok {hint}",
        }),
      )
    : new Response("", { status: 404 }),
);
const store = await import("../core/store.js");
const { parse, verdictOf } = await import("../core/grammar.js");

/** @param {unknown} value */
const copy = (value) => JSON.parse(JSON.stringify(value));

const VERBS = [
  { id: "ship", takesTask: true, hint: "ships" },
  { id: "go", aliases: ["start"], takesTask: false, hint: "goes" },
  { id: "open", takesTask: true, takesProject: true, hint: "opens" },
];
const TASKS = [{ id: "ABC-42", alias: "login" }];

/** @param {unknown} verbs */
const load = (verbs = VERBS) => store.set(copy({ verbs, tasks: TASKS }));

test("nothingIsParsed whenTheLineIsBlank", () => {
  load();

  assert.equal(parse("   "), null);
});

test("nothingIsParsed whenTheFirstWordIsNoVerb", () => {
  load();

  assert.equal(parse("hello there"), null);
});

test("theVerbIsFoundByAlias whenTheAliasIsTyped", () => {
  load();

  assert.equal(parse("START")?.verb.id, "go");
});

test("anIdBeatsAnAlias whenAnotherVerbCarriesItAsAlias", () => {
  load([
    { id: "a", aliases: ["b"], hint: "" },
    { id: "b", hint: "" },
  ]);

  assert.equal(parse("b")?.verb.id, "b");
});

test("theTaskIsResolved whenTheVerbTakesOne", () => {
  load();

  assert.equal(parse("ship login")?.task?.id, "ABC-42");
});

test("theRestIsTheProject whenTheTaskIsTheFirstWordOnly", () => {
  load();

  const parsed = parse("open ABC-42 my project");

  assert.deepEqual(
    [parsed?.task?.id, parsed?.project],
    ["ABC-42", "my project"],
  );
});

test("noTaskIsResolved whenTheVerbTakesOneAndNothingMatches", () => {
  load();

  assert.equal(parse("ship nothing")?.task, undefined);
});

test("theArgumentIsKept whenTheVerbTakesNoTask", () => {
  load();

  assert.equal(parse("go fast now")?.argument, "fast now");
});

test("noVerdictIsGiven whenTheLineIsEmpty", () => {
  load();

  assert.deepEqual(verdictOf(""), { text: "" });
});

test("aPlainWordsNoticeIsGiven whenTheLineIsNoCommand", () => {
  load();

  assert.deepEqual(verdictOf("hello there"), { text: "plain hello" });
});

test("theLineIsOk whenTheVerbTakesNoTask", () => {
  load();

  assert.deepEqual(verdictOf("go"), { kind: "ok", text: "ok goes" });
});

test("theLineIsBad whenTheTaskIsUnknown", () => {
  load();

  assert.deepEqual(verdictOf("ship nothing"), {
    kind: "bad",
    text: "no task nothing",
  });
});

test("theLineIsBad whenTheTaskIsMissing", () => {
  load();

  assert.deepEqual(verdictOf("ship"), {
    kind: "bad",
    text: "ship needs a task",
  });
});

test("theLineIsOk whenTheTaskIsKnown", () => {
  load();

  assert.deepEqual(verdictOf("ship login"), { kind: "ok", text: "ok ships" });
});
