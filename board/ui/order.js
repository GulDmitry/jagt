// In what ORDER the cards sit. Registration order is the default because it never moves a card that is already
// on screen: an alias is the lowest free number, so a task created after one was retired takes the retired
// one's place in an alias order and lands in the middle of the board.

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
