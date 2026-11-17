import assert from "node:assert/strict";
import { test } from "node:test";
import * as store from "../core/store.js";

test("theBoardIsEmptyAndOff whenNothingWasLoaded", () => {
  assert.deepEqual(
    [
      store.tasks(),
      store.projects(),
      store.reach(),
      store.verbs(),
      store.branchStrategies(),
      store.phases(),
      store.autoReview(),
      store.jobs(),
      store.actStop(),
      store.masterMode(),
    ],
    [
      [],
      [],
      [],
      [],
      [],
      [],
      { summary: "", enabled: false },
      null,
      null,
      "off",
    ],
  );
});
