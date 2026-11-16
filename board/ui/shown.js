import * as filters from "./filters.js";
import * as order from "./order.js";

export const shown = (tasks) => order.sorted(filters.shown(tasks));
