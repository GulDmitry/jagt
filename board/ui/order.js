import { byAlias } from "./aliasorder.js";

/** @typedef {import("../core/store.js").Task} Task */

const state = {
  byAlias: false,
};

export const holds = () => state.byAlias;

export const toggle = () => {
  state.byAlias = !state.byAlias;
};

/** @param {Task[]} tasks */
export const sorted = (tasks) => (state.byAlias ? byAlias(tasks) : tasks);
