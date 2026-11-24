/** @typedef {import("../core/store.js").Task} Task */
/** @typedef {import("../core/store.js").Action} Action */

/** @type {Map<string, { since: number, said: string }>} */
const state = new Map();

/**
 * @param {Task} task
 * @param {Action} action
 */
const key = (task, action) => `${task.id}:${action.id}`;

/**
 * @param {Task} task
 * @param {Action} action
 * @param {string} said
 */
export const mark = (task, action, said) =>
  state.set(key(task, action), { since: task.statusSince, said });

/**
 * @param {Task} task
 * @param {Action} action
 */
export const clear = (task, action) => state.delete(key(task, action));

/**
 * @param {Task} task
 * @returns {Map<string, string>} verb id to the answer its press got
 */
export const sayingOn = (task) =>
  new Map(
    (task.actions || []).flatMap((action) => {
      const held = state.get(key(task, action));
      return held?.since === task.statusSince ? [[action.id, held.said]] : [];
    }),
  );
