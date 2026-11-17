import assert from "node:assert/strict";
import { mock, test } from "node:test";

const FILES = {
  "i18n/en.json": {
    "header.filtered": "{on} filters hide {held}",
    "header.phase.on": "show {label}",
    "header.phase.off": "hide {label}",
    "header.order.alias": "by alias",
    "header.order.added": "by added",
    "header.order.alias.tip": "to alias",
    "header.order.added.tip": "to added",
    "header.clear": "clear {n}",
  },
};
mock.method(globalThis, "fetch", async (/** @type {string} */ url) =>
  url in FILES
    ? new Response(
        JSON.stringify(FILES[/** @type {keyof typeof FILES} */ (url)]),
      )
    : new Response("", { status: 404 }),
);
const { clearLabel, emptyFacts, orderFacts, segments } =
  await import("../ui/phasefacts.js");

test("theEmptyTextShows whenNothingIsHeld", () => {
  const facts = emptyFacts(0, 0, 0);

  assert.deepEqual([facts.emptyHidden, facts.filteredHidden], [false, true]);
});

test("theFilteredTextShows whenEverythingIsFilteredOut", () => {
  const facts = emptyFacts(3, 0, 1);

  assert.deepEqual(
    [facts.emptyHidden, facts.filteredHidden, facts.filtered],
    [true, false, "1 filters hide 3"],
  );
});

test("neitherTextShows whenSomethingIsShown", () => {
  const facts = emptyFacts(3, 2, 1);

  assert.deepEqual([facts.emptyHidden, facts.filteredHidden], [true, true]);
});

test("aPhaseCountsItsTasksAndTipsItsToggle whenHeldOrNot", () => {
  const phases = [
    { id: "WORK", label: "Work" },
    { id: "DONE", label: "Done" },
  ];
  const tasks = [{ phase: "WORK" }, { phase: "WORK" }];

  const built = segments(
    phases,
    /** @type {never} */ (tasks),
    (phase) => phase === "WORK",
  );

  assert.deepEqual(
    built.map((one) => [one.held, one.className, one.pressed, one.tip]),
    [
      [2, "phase", true, "hide Work"],
      [0, "phase empty", false, "show Done"],
    ],
  );
});

test("theOrderNamesTheCurrentAndTipsTheOther whenSortedByAlias", () => {
  assert.deepEqual(orderFacts(true), {
    label: "by alias",
    tip: "to added",
  });
});

test("theOrderNamesTheCurrentAndTipsTheOther whenSortedByAdded", () => {
  assert.deepEqual(orderFacts(false), {
    label: "by added",
    tip: "to alias",
  });
});

test("theClearButtonSaysTheCount whenFiltersAreOn", () => {
  assert.equal(clearLabel(2), "clear 2");
});
