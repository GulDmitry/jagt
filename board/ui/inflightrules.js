// TWO questions, because they refuse different clicks: this exact button, and any other WRITING one on the task.

/** @typedef {import("../core/store.js").Task} Task */
/** @typedef {import("../core/store.js").Action} Action */
/** @typedef {{ pending: Set<string>, writing: Map<string, string> }} Inflight */

/** @returns {Inflight} */
export const create = () => ({ pending: new Set(), writing: new Map() });

/**
 * @param {Task} task
 * @param {Action} action
 */
const key = (task, action) => `${task.id}:${action.id}`;

/**
 * @param {Inflight} state
 * @param {Task} task
 * @param {Action} action
 */
export const blocked = (state, task, action) =>
  state.pending.has(key(task, action)) ||
  (!action.readOnly && state.writing.has(task.id));

/**
 * @param {Inflight} state
 * @param {Task} task
 * @param {Action} action
 */
export const waitingFor = (state, task, action) =>
  state.writing.get(task.id) || action.id;

/**
 * @param {Inflight} state
 * @param {Task} task
 * @param {Action} action
 */
export const hold = (state, task, action) => {
  state.pending.add(key(task, action));
  if (!action.readOnly) state.writing.set(task.id, action.id);
};

/**
 * @param {Inflight} state
 * @param {Task} task
 * @param {Action} action
 */
export const release = (state, task, action) => {
  state.pending.delete(key(task, action));
  if (!action.readOnly) state.writing.delete(task.id);
};
