import assert from "node:assert/strict";
import { test } from "node:test";
import { serveFiles } from "./fakefetch.js";

const FILES = { "i18n/en.json": { "stop.at": "stopped {at}" } };
serveFiles(FILES);
const { originLabel, stopLines, switchLines } =
  await import("../core/format.js");

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
