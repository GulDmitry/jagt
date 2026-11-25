import assert from "node:assert/strict";
import { test } from "node:test";
import { broken, parse } from "../core/logline.js";

test("newestEntryComesFirst whenTheLogIsWrittenOldestFirst", () => {
  const { entries } = parse(
    "2026-11-25 09:00:01Z INFO moved ABC-42\n2026-11-25 09:00:02Z WARN refused ABC-42",
  );

  assert.deepEqual(
    entries.map((entry) => entry.text),
    [["refused ABC-42"], ["moved ABC-42"]],
  );
});

test("timeIsTheClockInUtc whenTheLineCarriesADateAndAZone", () => {
  const { entries } = parse("2026-11-25 09:00:01Z INFO moved");

  assert.equal(entries[0].time, "09:00:01");
});

test("timeIsTheClockInUtc whenTheLineCarriesOnlyMonthAndDay", () => {
  const { entries } = parse("11-25 09:00:01 ERROR failed");

  assert.deepEqual([entries[0].time, entries[0].level], ["09:00:01", "ERROR"]);
});

test("timeIsTheClockInUtc whenTheLineCarriesOnlyTheClock", () => {
  const { entries } = parse("09:00:01 INFO saved");

  assert.deepEqual([entries[0].time, entries[0].text], ["09:00:01", ["saved"]]);
});

test("lineBelongsToItsEntry whenItDoesNotStartOne", () => {
  const { entries } = parse(
    "09:00:01 INFO first\n  wrapped\n09:00:02 INFO next",
  );

  assert.deepEqual(entries[1].text, ["first", "  wrapped"]);
});

test("headHoldsWhatPrecedesTheFirstEntry whenTheReportOpensWithAHeading", () => {
  const { head, entries } = parse(
    "the last 1 lines (UTC):\n09:00:01 INFO saved",
  );

  assert.deepEqual([head, entries.length], [["the last 1 lines (UTC):"], 1]);
});

test("seqCountsFromTheOldest whenEntriesArrive", () => {
  const { entries } = parse("09:00:01 INFO a\n09:00:02 INFO b");

  assert.deepEqual(
    entries.map((entry) => entry.seq),
    [1, 0],
  );
});

test("onlyWarnAndErrorAreBroken whenLevelsAreAsked", () => {
  assert.deepEqual(["INFO", "WARN", "ERROR", "DEBUG"].map(broken), [
    false,
    true,
    true,
    false,
  ]);
});
