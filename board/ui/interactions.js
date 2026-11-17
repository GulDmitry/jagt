import { api } from "../core/api.js";
import { closest, within } from "../core/dom.js";
import * as store from "../core/store.js";

const BATCH = 50;
const QUEUE = 200;
const SETTLE = 2000;

/** @type {{ queue: { at: number, control: string, status: string | null }[], due: number, required: Set<string> | null, jobsFailed: boolean, sighting: boolean }} */
const state = {
  queue: [],
  due: 0,
  required: null,
  jobsFailed: false,
  sighting: !navigator.locks,
};

/** @param {HTMLElement | SVGElement} node */
const named = (node) => {
  const { action, verb, phase, place } = node.dataset;
  if (action) return place ? `action:${action}.${place}` : `action:${action}`;
  if (node.hasAttribute("data-select")) return "select";
  if (node.hasAttribute("data-back")) return "back";
  if (verb) return `verb:${verb}`;
  if (phase) return "phase";
  if (node.hasAttribute("data-flag")) return "flag";
  if (node.hasAttribute("data-order")) return "order";
  if (node.hasAttribute("data-clear")) return "clear-filters";
  if (node.id) return `id:${node.id}`;
  return node.classList.length ? `class:${node.classList[0]}` : null;
};

// The server refuses a whole batch over one malformed name, so a name is made to fit before it is queued.
/** @param {string} name */
const fitted = (name) =>
  name
    .toLowerCase()
    .replace(/[^a-z0-9:#._-]/g, "-")
    .replace(/^[^a-z0-9]+/, "")
    .slice(0, 48);

/**
 * @param {string | null} name
 * @param {string} [taskId]
 */
export const note = (name, taskId) => {
  const control = name && fitted(name);
  if (!control) return;
  state.queue.push({
    at: Date.now(),
    control,
    status: (taskId && store.taskById(taskId)?.status) || null,
  });
  if (state.queue.length > QUEUE) state.queue = state.queue.slice(-QUEUE);
  if (state.queue.length >= BATCH) flush();
  else if (!state.due) state.due = setTimeout(flush, SETTLE);
};

// One board per browser logs sightings, the next open one taking over when it closes.
navigator.locks?.request(
  "jagt-sightings",
  () => new Promise(() => (state.sighting = true)),
);

// A sighting is logged when it BEGINS: the first board seen only seeds, since when it turned required is unknown.
/**
 * @param {import("../core/store.js").Task[]} tasks
 * @param {boolean} failing
 */
export function watch(tasks, failing) {
  if (failing && !state.jobsFailed) note("shown:jobs-failed");
  state.jobsFailed = failing;
  if (!tasks.length) return;
  const now = new Set(
    tasks
      .filter((task) => task.attention === "REQUIRED")
      .map((task) => task.id),
  );
  const before = state.required;
  if (before && state.sighting)
    tasks
      .filter((task) => now.has(task.id) && !before.has(task.id))
      .forEach((task) => note("shown:required", task.id));
  state.required = now;
}

function flush() {
  clearTimeout(state.due);
  state.due = 0;
  if (!state.queue.length) return;
  const presses = state.queue.splice(0, BATCH);
  if (state.queue.length) state.due = setTimeout(flush, SETTLE);
  api("/api/interactions", {
    method: "POST",
    keepalive: true,
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ presses }),
  }).catch(() => {});
}

document.addEventListener(
  "click",
  (event) => {
    const control = closest(event, "button, a, input[type=checkbox], select");
    if (control)
      note(
        named(control),
        within(control, "[data-task]")?.dataset.task ||
          within(control, "[data-row]")?.dataset.row,
      );
  },
  true,
);
document.addEventListener(
  "submit",
  (event) => event.target instanceof HTMLElement && note(named(event.target)),
  true,
);
window.addEventListener("pagehide", flush);
