import assert from "node:assert/strict";
import { afterEach, mock, test } from "node:test";
import {
  api,
  cause,
  pressKey,
  refusal,
  runTo,
  sayTo,
  sayToWindow,
  text,
} from "../core/api.js";

/**
 * @param {BodyInit | null} body
 * @param {ResponseInit} [init]
 */
const answer = (body, init) =>
  mock.method(globalThis, "fetch", async () => new Response(body, init));

afterEach(() => mock.restoreAll());

test("theBodyIsReturned whenTheAnswerIsOk", async () => {
  answer('{"a":1}');

  assert.deepEqual(await api("/api/x"), { a: 1 });
});

test("anEmptyObjectIsReturned whenTheOkBodyIsNotJson", async () => {
  answer("plain");

  assert.deepEqual(await api("/api/x"), {});
});

test("theFailureCarriesCodeAndSentence whenTheServerRefuses", async () => {
  answer('{"error":"no such task","code":"NO_SUCH_TASK"}', { status: 404 });

  await assert.rejects(api("/api/x"), {
    message: "no such task",
    code: "NO_SUCH_TASK",
  });
});

test("theStatusIsTheSentence whenTheRefusalHasNoBody", async () => {
  answer("", { status: 500, statusText: "Broken" });

  await assert.rejects(api("/api/x"), { message: "500 Broken" });
});

test("theTextIsReturned whenTheAnswerIsOk", async () => {
  answer("hello");

  assert.equal(await text("/x"), "hello");
});

test("theErrorFieldIsTheSentence whenTheTextRefusalIsJson", async () => {
  answer('{"error":"nope","code":"C"}', { status: 400 });

  await assert.rejects(text("/x"), { message: "nope", code: "C" });
});

test("theWholeBodyIsTheSentence whenTheTextRefusalIsNotJson", async () => {
  answer("gone wrong", { status: 400 });

  await assert.rejects(text("/x"), { message: "gone wrong" });
});

test("theWholeBodyIsTheSentence whenTheTextRefusalHasNoError", async () => {
  answer('{"other":1}', { status: 400 });

  await assert.rejects(text("/x"), { message: '{"other":1}' });
});

/** @type {[string, (a: string, b: string) => Promise<unknown>, string, string][]} */
const SENDS = [
  ["sayTo", sayTo, "/api/tasks/say?task=a%20b", '{"line":"go"}'],
  ["sayToWindow", sayToWindow, "/api/windows/say?name=a%20b", '{"line":"go"}'],
  ["runTo", runTo, "/api/tasks/run?task=a%20b", '{"line":"go"}'],
  ["pressKey", pressKey, "/api/tasks/key?task=a%20b", '{"key":"go"}'],
];

SENDS.forEach(([name, send, url, body]) =>
  test(`${name}PostsJson whenCalled`, async () => {
    const fetched = answer("{}");

    await send("a b", "go");

    const [path, options] = fetched.mock.calls[0].arguments;
    assert.deepEqual(
      [path, options?.method, options?.body],
      [url, "POST", body],
    );
  }),
);

test("theViewIsSaidToBeCurrent whenTheTaskIsGone", () => {
  const gone = Object.assign(new Error("no task"), { code: "NO_SUCH_TASK" });

  assert.equal(refusal(gone), "no task\n\nrefusal.current");
});

test("theMessageIsShown whenTheCodeIsNotStale", () => {
  const other = Object.assign(new Error("boom"), { code: "OTHER" });

  assert.equal(refusal(other), "boom");
});

test("theValueIsShown whenTheRefusalIsNoError", () => {
  assert.equal(refusal("odd"), "odd");
});

test("theCauseIsTheMessage whenTheErrorIsAnError", () => {
  assert.equal(cause(new Error("boom")), "boom");
});

test("theCauseIsTheValue whenTheErrorIsNoError", () => {
  assert.equal(cause(7), "7");
});
