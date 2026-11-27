import assert from "node:assert/strict";
import { test } from "node:test";
import { serveFiles } from "./fakefetch.js";

const FILES = { "i18n/en.json": { "stop.at": "stopped {at}" } };
serveFiles(FILES);
const { reportParts } = await import("../core/format.js");

test("aWebAddressBecomesALink whenTheLineContainsOne", () => {
  assert.deepEqual(reportParts("see https://example.org/a now"), [
    { kind: "text", text: "see " },
    {
      kind: "link",
      text: "https://example.org/a",
      href: "https://example.org/a",
    },
    { kind: "text", text: " now" },
  ]);
});

test("sentencePunctuationStaysOutOfTheLink whenTheAddressEndsTheSentence", () => {
  assert.deepEqual(reportParts("see https://example.org/a."), [
    { kind: "text", text: "see " },
    {
      kind: "link",
      text: "https://example.org/a",
      href: "https://example.org/a",
    },
    { kind: "text", text: "." },
  ]);
});

test("noLinkIsMade whenTheAddressIsNotHttp", () => {
  assert.deepEqual(reportParts("open javascript:alert(1)"), [
    { kind: "text", text: "open javascript:alert(1)" },
  ]);
});

test("theVerdictIsToneOk whenTheHeadSaysFixed", () => {
  assert.deepEqual(reportParts("  3 · FIXED · done"), [
    {
      kind: "block",
      parts: [
        { kind: "text", text: "  3 · " },
        { kind: "verdict", text: "FIXED ", tone: "ok" },
        { kind: "text", text: "· done" },
      ],
    },
  ]);
});

test("theVerdictIsToneYou whenTheHeadSaysQuestion", () => {
  const [block] = reportParts("1 · QUESTION · which?");

  assert.equal(block.parts?.[1].tone, "you");
});

test("theVerdictIsToneMuted whenTheHeadSaysAnythingElse", () => {
  const [block] = reportParts("1 · REJECTED · no");

  assert.equal(block.parts?.[1].tone, "muted");
});

test("aQuotedLineIsAQuote whenItStartsWithAngleBracket", () => {
  assert.deepEqual(reportParts("> said so"), [
    { kind: "quote", parts: [{ kind: "text", text: "> said so" }] },
  ]);
});

test("linesAreSeparatedByANewlinePart whenTheReportHasTwoLines", () => {
  assert.deepEqual(reportParts("one\ntwo"), [
    { kind: "text", text: "one" },
    { kind: "text", text: "\n" },
    { kind: "text", text: "two" },
  ]);
});

test("trailingBlankLinesAreDropped whenTheReportEndsWithNewlines", () => {
  assert.deepEqual(reportParts("one\n\n"), [{ kind: "text", text: "one" }]);
});

test("aTaskIdBecomesATaskPart whenTheBoardStillHoldsThatTask", () => {
  assert.deepEqual(reportParts("fix ABC-42 now", ["ABC-42"]), [
    { kind: "text", text: "fix " },
    { kind: "task", text: "ABC-42" },
    { kind: "text", text: " now" },
  ]);
});

test("noTaskPartIsMade whenTheIdIsInsideALongerName", () => {
  assert.deepEqual(reportParts("fix XABC-42 now", ["ABC-42"]), [
    { kind: "text", text: "fix XABC-42 now" },
  ]);
});

test("regexCharactersInAnIdAreLiteral whenTheIdContainsADot", () => {
  assert.deepEqual(reportParts("a.b and axb", ["a.b"]), [
    { kind: "text", text: "" },
    { kind: "task", text: "a.b" },
    { kind: "text", text: " and axb" },
  ]);
});
