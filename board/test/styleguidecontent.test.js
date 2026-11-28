import assert from "node:assert/strict";
import { test } from "node:test";
import { cardSection } from "../styleguide/cardSection.js";
import { cards } from "../styleguide/cards.js";
import { checksSection, jobsSection } from "../styleguide/jobsSection.js";

const mainOf = (/** @type {any} */ section) =>
  section.props.children.find((/** @type {any} */ c) => c.type === "main");

const table = () =>
  mainOf(jobsSection).props.children.props.children.props.children;

/** @param {string} id */
const job = (id) =>
  table().props.jobs.find((/** @type {{id: string}} */ each) => each.id === id);

test("eachCardFrameIsNamedAndHoldsItsCard whenTheCardSectionIsBuilt", () => {
  const frames = mainOf(cardSection).props.children;

  assert.deepEqual(
    frames.map((/** @type {any} */ frame) => frame.props.name),
    cards.map(({ name }) => name),
  );
  assert.deepEqual(
    frames.map((/** @type {any} */ frame) => frame.props.children.props.task),
    cards.map(({ props }) => props.task),
  );
});

test("eachJobSaysWhatItDoesAndWhoItServes whenTheJobsSectionIsBuilt", () => {
  assert.deepEqual(
    table().props.jobs.map(
      (/** @type {any} */ each) => `${each.describe} | ${each.serves}`,
    ),
    [
      "runs the project’s verify command | your tasks",
      "starts a task on every item marked ready | the tracker",
      "reads the subscription’s limits | housekeeping",
      "removes the worktrees of closed tasks | housekeeping",
    ],
  );
});

test("theFailedJobStartedInThePastAndRunsSoon whenTheJobsSectionIsBuilt", () => {
  const { now } = table().props;
  const verify = job("verify");

  assert.equal(verify.lastError, "exit 1: 2 tests fail");
  assert.equal(verify.running, false);
  assert.ok(now - verify.lastStartedAt > 300000);
  assert.ok(verify.nextRunAt - now > 0);
});

test("theNeverRunJobWaitsForItsFirstRun whenTheJobsSectionIsBuilt", () => {
  const { now } = table().props;
  const intake = job("intake");

  assert.equal(intake.lastStartedAt, null);
  assert.equal(intake.lastError, null);
  assert.equal(intake.running, false);
  assert.ok(intake.nextRunAt - now > 0);
});

test("theRunningJobHasNoNextRun whenTheJobsSectionIsBuilt", () => {
  const usage = job("usage");

  assert.equal(usage.running, true);
  assert.equal(usage.nextRunAt, null);
});

test("thePassedJobStartedInThePastAndRunsLater whenTheJobsSectionIsBuilt", () => {
  const { now } = table().props;
  const cleanup = job("cleanup");

  assert.equal(cleanup.lastError, null);
  assert.equal(cleanup.running, false);
  assert.ok(now - cleanup.lastStartedAt > 500000);
  assert.ok(cleanup.nextRunAt - now > 0);
});

test("theChecksSayWhatTheyFound whenTheChecksSectionIsBuilt", () => {
  const list = mainOf(checksSection).props.children.props.children;

  assert.deepEqual(
    list.props.children.props.lines.map(
      (/** @type {{text: string}} */ line) => line.text,
    ),
    [
      "a git repository, origin git@host:g/billing.git",
      "tasks are cut from origin/main",
      "the key billing is taken: pick another",
    ],
  );
});
