import assert from "node:assert/strict";
import { test } from "node:test";
import { originLabel, reportParts } from "../core/format.js";

test("anEmptyLabelIsGiven whenTheStepHasNoOrigin", () => {
  assert.equal(originLabel({}), "");
});

test("anHttpAddressBecomesALink whenItIsNotHttps", () => {
  assert.deepEqual(reportParts("http://example.org/a"), [
    {
      kind: "link",
      text: "http://example.org/a",
      href: "http://example.org/a",
    },
    { kind: "text", text: "" },
  ]);
});

test("everyTrailingPunctuationStaysOutOfTheLink whenTheAddressEndsInSeveral", () => {
  assert.deepEqual(reportParts("see https://example.org/a?!"), [
    { kind: "text", text: "see " },
    {
      kind: "link",
      text: "https://example.org/a",
      href: "https://example.org/a",
    },
    { kind: "text", text: "?!" },
  ]);
});

test("noVerdictIsMade whenTheNumberedHeadIsNotAtTheLineStart", () => {
  assert.deepEqual(reportParts("see 3 · FIXED · done"), [
    { kind: "text", text: "see 3 · FIXED · done" },
  ]);
});

test("aMultiDigitNumberHeadsAVerdict whenTheRoundIsAboveNine", () => {
  assert.equal(reportParts("12 · FIXED · done")[0].kind, "block");
});

test("noQuoteIsMade whenTheAngleBracketIsMidLine", () => {
  assert.deepEqual(reportParts("a > b"), [{ kind: "text", text: "a > b" }]);
});

test("anIndentedLineIsAQuote whenTheAngleBracketFollowsSpaces", () => {
  assert.equal(reportParts("  > said")[0].kind, "quote");
});

test("everyIdBecomesATaskPart whenSeveralTasksAreOnTheBoard", () => {
  assert.deepEqual(reportParts("ABC-42 and ABC-43", ["ABC-42", "ABC-43"]), [
    { kind: "text", text: "" },
    { kind: "task", text: "ABC-42" },
    { kind: "text", text: " and " },
    { kind: "task", text: "ABC-43" },
    { kind: "text", text: "" },
  ]);
});

test("aLinkStaysALink whenItsAddressContainsATaskId", () => {
  assert.deepEqual(reportParts("https://example.org/ABC-42", ["ABC-42"]), [
    {
      kind: "link",
      text: "https://example.org/ABC-42",
      href: "https://example.org/ABC-42",
    },
    { kind: "text", text: "" },
  ]);
});
