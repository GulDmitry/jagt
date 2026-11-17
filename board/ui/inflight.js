// TWO questions, because they refuse different clicks: this exact button, and any other WRITING one on the task.

/** @typedef {import("../core/store.js").Task} Task */
/** @typedef {import("../core/store.js").Action} Action */

/** @type {Set<string>} */
const pending = new Set();
/** @type {Map<string, string>} */
const writing = new Map();

/**
 * @param {Task} task
 * @param {Action} action
 */
const key = (task, action) => `${task.id}:${action.id}`;

// What DISABLES a button also refuses the click: a typed line reaches `run` with no button in between.
/**
 * @param {Task} task
 * @param {Action} action
 */
export const blocked = (task, action) =>
  pending.has(key(task, action)) || (!action.readOnly && writing.has(task.id));

/**
 * @param {Task} task
 * @param {Action} action
 */
export const waitingFor = (task, action) => writing.get(task.id) || action.id;

/**
 * @param {Task} task
 * @param {Action} action
 */
export function hold(task, action) {
  pending.add(key(task, action));
  if (!action.readOnly) writing.set(task.id, action.id);
}

/**
 * @param {Task} task
 * @param {Action} action
 */
export function release(task, action) {
  pending.delete(key(task, action));
  if (!action.readOnly) writing.delete(task.id);
}
