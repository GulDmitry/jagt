import assert from "node:assert/strict";
import { test } from "node:test";
import {
  bothNames,
  countdown,
  duration,
  originLabel,
  reportParts,
  stopLines,
  switchLines,
} from "../core/format.js";

/** @type {[number, string][]} */
const SPANS = [
  [0, "0m"],
  [-5000, "0m"],
  [59999, "0m"],
  [60000, "1m"],
  [3540000, "59m"],
  [3600000, "1h"],
  [86340000, "23h"],
  [86400000, "1d"],
  [172800000, "2d"],
];

SPANS.forEach(([millis, shown]) =>
  test(`durationReads ${shown} whenTheSpanIs${millis}Millis`, () => {
    assert.equal(duration(millis), shown);
  }),
);

/** @type {[number, string][]} */
const WAITS = [
  [0, "0s"],
  [-1000, "0s"],
  [59999, "59s"],
  [60000, "1m"],
  [61000, "2m"],
  [600000, "10m"],
  [3540000, "59m"],
  [3600000, "1h"],
  [3660000, "2h"],
];

WAITS.forEach(([millis, shown]) =>
  test(`countdownReads ${shown} whenTheWaitIs${millis}Millis`, () => {
    assert.equal(countdown(millis), shown);
  }),
);

test("bothNamesJoinAliasAndId whenTheTaskHasAnAlias", () => {
  assert.equal(bothNames({ id: "ABC-42", alias: "login" }), "login · ABC-42");
});

test("onlyTheIdIsShown whenTheTaskHasNoAlias", () => {
  assert.equal(bothNames({ id: "ABC-42" }), "ABC-42");
});

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

test("switchesReadNameAndSaying whenTheTaskHasSwitches", () => {
  assert.deepEqual(switchLines({ switches: { review: "off", deploy: "on" } }), [
    "review: off",
    "deploy: on",
  ]);
});

test("noSwitchLinesExist whenTheTaskHasNoSwitches", () => {
  assert.deepEqual(switchLines({}), []);
});

test("stopLinesKeepOnlyWhatWasSaid whenTheStopHasNoTriage", () => {
  assert.deepEqual(
    stopLines({
      stop: {
        evidence: "ci red",
        next: "fix it",
        at: Date.UTC(2026, 10, 17, 9, 5),
      },
    }),
    ["ci red", "fix it", "stopped 17 Nov 2026, 09:05 UTC"],
  );
});

test("noStopLinesExist whenTheTaskIsNotStopped", () => {
  assert.deepEqual(stopLines({}), []);
});

test("historyLabelNamesTheCheckerRole whenAnAcceptanceStepCarriesOne", () => {
  assert.equal(
    originLabel({ origin: "ACCEPTANCE", role: "security" }),
    "security",
  );
});

test("historyLabelKeepsAcceptance whenTheStepCarriesNoRole", () => {
  assert.equal(originLabel({ origin: "ACCEPTANCE" }), "acceptance");
});

test("historyLabelKeepsTheOrigin whenAnotherOriginCarriesARole", () => {
  assert.equal(
    originLabel({ origin: "AUTO_REVIEW", role: "security" }),
    "auto-review",
  );
});

test("onlyAHeaderIsMuted whenALineMerelyContainsDiffWords", async () => {
  const { diffLineClass } = await import("../core/format.js");

  assert.deepEqual(
    ["x diff here", "  index of", "a @@ b", "--- a/f", "@@ -1 +1 @@"].map(
      diffLineClass,
    ),
    ["", "", "", "muted", "muted"],
  );
});
