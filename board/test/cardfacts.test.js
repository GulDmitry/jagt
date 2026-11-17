import assert from "node:assert/strict";
import { mock, test } from "node:test";

const FILES = {
  "i18n/en.json": { "card.tier.tip": "runs on {model}" },
};
mock.method(globalThis, "fetch", async (/** @type {string} */ url) =>
  url in FILES
    ? new Response(
        JSON.stringify(FILES[/** @type {keyof typeof FILES} */ (url)]),
      )
    : new Response("", { status: 404 }),
);
const { actionRows, badgeTip, detailClass, flagTexts, titleLines, verbClass } =
  await import("../components/cardfacts.js");

/** @param {object} fields */
const task = (fields) => /** @type {never} */ (fields);

test("aVerbDrawnApartIsNeverPrimary whenItIsBoth", () => {
  assert.equal(verbClass(task({ primary: true, apart: true })), "apart");
});

test("aVerbHasNoClass whenItIsPlain", () => {
  assert.equal(verbClass(task({})), undefined);
});

test("aPrimaryDeployAgainNamesBoth whenItIsAnAgainVerb", () => {
  assert.equal(
    verbClass(task({ primary: true, again: true })),
    "primary again",
  );
});

test("verbsGroupInOrderAndApartEndsTheRow whenGroupsAlternate", () => {
  const rows = actionRows(
    task({
      actions: [
        { id: "a", group: "g1", apart: true },
        { id: "b", group: "g1" },
        { id: "c", group: "g2" },
        { id: "s", group: "session" },
      ],
    }),
  );

  assert.deepEqual(
    rows.map((row) => [row.group, row.items.map((item) => item.action.id)]),
    [
      ["g1", ["b", "a"]],
      ["g2", ["c"]],
    ],
  );
});

test("aProblemIsBroken whateverTheTierSays", () => {
  assert.equal(
    detailClass(task({ detailKind: "PROBLEM", attention: "OPTIONAL" })),
    "detail problem",
  );
});

test("aMoveOfYoursIsColoured whenTheTierIsNotOptional", () => {
  assert.equal(
    detailClass(task({ detailKind: "YOURS", attention: "REQUIRED" })),
    "detail you",
  );
});

test("aMoveOfYoursDropsItsColour whenTheTierIsOptional", () => {
  assert.equal(
    detailClass(task({ detailKind: "YOURS", attention: "OPTIONAL" })),
    "detail",
  );
});

test("theTitleNamesTheTierAndSwitches whenBothAreSet", () => {
  const lines = titleLines(task({ tier: "heavy", switches: { a: "on" } }));

  assert.deepEqual(lines, ["runs on heavy", "a: on"]);
});

test("theBadgeTipListsHintAndStop whenTheTaskIsStopped", () => {
  const tip = badgeTip(
    task({ hint: "look", stop: { evidence: "why", at: 1763370300000 } }),
  );

  assert.equal(tip, "look\nwhy\nstopped 17 Nov 2025, 09:05 UTC");
});

test("theFlagIsNamedAndTipped whenItIsDrawn", () => {
  assert.deepEqual(flagTexts(), { label: "card.flag", tip: "card.flag.tip" });
});

test("theTitleHasNoLines whenThereIsNoTierNorSwitch", () => {
  assert.deepEqual(titleLines(task({})), []);
});

test("aMoveIsPlain whenItIsNeitherAProblemNorYours", () => {
  assert.equal(
    detailClass(task({ detailKind: "THEIRS", attention: "REQUIRED" })),
    "detail",
  );
});
