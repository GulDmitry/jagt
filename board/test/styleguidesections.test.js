import assert from "node:assert/strict";
import { test } from "node:test";
import { cardSection } from "../styleguide/cardSection.js";
import { cards } from "../styleguide/cards.js";
import { checksSection, jobsSection } from "../styleguide/jobsSection.js";

const mainOf = (/** @type {any} */ section) =>
  section.props.children.find((/** @type {any} */ c) => c.type === "main");

test("everyCardStateHasItsOwnFrame whenTheCardSectionIsBuilt", () => {
  assert.equal(mainOf(cardSection).props.children.length, cards.length);
});

test("fourJobsAreShown whenTheJobsSectionIsBuilt", () => {
  const table = mainOf(jobsSection).props.children.props.children;

  assert.deepEqual(
    table.props.jobs.map((/** @type {{id: string}} */ job) => job.id),
    ["verify", "intake", "usage", "cleanup"],
  );
});

test("oneCheckFails whenTheChecksSectionIsBuilt", () => {
  const list = mainOf(checksSection).props.children.props.children;

  assert.deepEqual(
    list.props.children.props.lines.map(
      (/** @type {{ok: boolean}} */ line) => line.ok,
    ),
    [true, true, false],
  );
});
