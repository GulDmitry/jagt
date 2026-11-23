import * as rules from "./inflightrules.js";

/** @typedef {import("../core/store.js").Task} Task */
/** @typedef {import("../core/store.js").Action} Action */

const state = rules.create();

/**
 * @param {Task} task
 * @param {Action} action
 */
export const blocked = (task, action) => rules.blocked(state, task, action);

/**
 * @param {Task} task
 * @param {Action} action
 */
export const waitingFor = (task, action) =>
  rules.waitingFor(state, task, action);

/**
 * @param {Task} task
 * @param {Action} action
 */
export const hold = (task, action) => rules.hold(state, task, action);

/**
 * @param {Task} task
 * @param {Action} action
 */
export const release = (task, action) => rules.release(state, task, action);
