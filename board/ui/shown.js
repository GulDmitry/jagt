import * as filters from "./filters.js";
import * as order from "./order.js";

/** @param {import("../core/store.js").Task[]} tasks */
export const shown = (tasks) => order.sorted(filters.shown(tasks));
