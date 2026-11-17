import assert from "node:assert/strict";
import { test } from "node:test";
import {
  dayAndMinutes,
  full,
  minutes,
  sameDay,
  stamp,
  withSeconds,
} from "../core/time.js";

const MORNING = Date.UTC(2026, 10, 17, 9, 5, 7);

test("secondsAreShownInUtc whenAnInstantIsFormattedWithSeconds", () => {
  assert.equal(withSeconds(MORNING), "09:05:07");
});

test("minutesAreShownInUtc whenAnInstantIsFormattedWithMinutes", () => {
  assert.equal(minutes(MORNING), "09:05");
});

test("dayAndMinutesAreShownInUtc whenAnInstantIsFormattedWithItsDay", () => {
  assert.equal(dayAndMinutes(MORNING), "17 Nov, 09:05");
});

test("fullTimeNamesTheYearAndUtc whenAnInstantIsFormattedInFull", () => {
  assert.equal(full(MORNING), "17 Nov 2026, 09:05 UTC");
});

test("fullTimeAcceptsADate whenTheInstantIsADateObject", () => {
  assert.equal(full(new Date(MORNING)), "17 Nov 2026, 09:05 UTC");
});

test("twoInstantsShareADay whenBothFallBetweenUtcMidnights", () => {
  assert.equal(
    sameDay(Date.UTC(2026, 10, 17, 0, 0), Date.UTC(2026, 10, 17, 23, 59)),
    true,
  );
});

test("twoInstantsDoNotShareADay whenUtcMidnightSeparatesThem", () => {
  assert.equal(
    sameDay(Date.UTC(2026, 10, 17, 23, 59), Date.UTC(2026, 10, 18, 0, 0)),
    false,
  );
});

test("onlyTheMinutesAreShown whenTheStepIsFromTheSameDay", () => {
  assert.equal(stamp(MORNING, MORNING + 3600000), "09:05");
});

test("theDayIsShownToo whenTheStepIsFromAnEarlierDay", () => {
  assert.equal(stamp(MORNING, MORNING + 86400000), "17 Nov, 09:05");
});
