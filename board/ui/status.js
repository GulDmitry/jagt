import * as store from "../core/store.js";
import * as glance from "./glance.js";
import { watch } from "./interactions.js";

/** @param {import("../core/store.js").Task[]} tasks */
export function status(tasks) {
  glance.render({
    waiting: tasks.filter((task) => task.attention === "REQUIRED").length,
    broken:
      tasks.some((task) => task.detailKind === "PROBLEM") ||
      Boolean(store.jobs()?.failing.length) ||
      store
        .chips()
        .some((chip) => chip.kind === "BAD" || chip.kind === "STALLED") ||
      store.reach().length > 0,
  });
  watch(tasks, Boolean(store.jobs()?.failing.length));
}
