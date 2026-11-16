import { api } from "../core/api.js";
import * as store from "../core/store.js";

const BATCH = 50;
const QUEUE = 200;
const SETTLE = 2000;

const state = {
  queue: [],
  due: 0,
  required: null,
  jobsFailed: false,
  sighting: !navigator.locks,
};

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
const fitted = (name) =>
  name
    .toLowerCase()
    .replace(/[^a-z0-9:#._-]/g, "-")
    .replace(/^[^a-z0-9]+/, "")
    .slice(0, 48);

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
export function watch(tasks, failing) {
  if (failing && !state.jobsFailed) note("shown:jobs-failed");
  state.jobsFailed = failing;
  if (!tasks.length) return;
  const now = new Set(
    tasks
      .filter((task) => task.attention === "REQUIRED")
      .map((task) => task.id),
  );
  if (state.required && state.sighting)
    tasks
      .filter((task) => now.has(task.id) && !state.required.has(task.id))
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
    const control = event.target.closest?.(
      "button, a, input[type=checkbox], select",
    );
    if (control)
      note(
        named(control),
        control.closest("[data-task]")?.dataset.task ||
          control.closest("[data-row]")?.dataset.row,
      );
  },
  true,
);
document.addEventListener("submit", (event) => note(named(event.target)), true);
window.addEventListener("pagehide", flush);
