import { grouped } from "../components/TaskList.js";
import * as store from "../core/store.js";
import * as flags from "./flags.js";
import * as selection from "./selection.js";
import { shown } from "./shown.js";

/** @typedef {ReturnType<typeof view>} View */
/** @typedef {Omit<View, "open"> & { open: import("../core/store.js").Task }} OpenView */

/** @type {{ walked: import("../core/store.js").Task[] }} */
const state = {
  walked: [],
};

export const ordered = () => state.walked;

export function view() {
  const tasks = store.tasks();
  const showing = shown(tasks);
  // Read off the cards: the configured list can differ from the truth.
  const manyProjects =
    new Set(
      tasks.flatMap((task) => (task.repos || []).map((repo) => repo.project)),
    ).size > 1;
  const groups = grouped(showing, manyProjects, (task) => flags.holds(task.id));
  state.walked = groups.flatMap((group) => group.tasks);
  const open = selection.current(state.walked);
  return {
    tasks,
    shown: showing,
    groups,
    manyProjects,
    open,
    flagged: open ? flags.holds(open.id) : false,
    now: Date.now(),
  };
}
