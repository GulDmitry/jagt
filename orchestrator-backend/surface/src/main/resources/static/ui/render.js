// One repaint of everything: a card that changes in place keeps the position a human has learnt.

import * as store from '../core/store.js';
import {card} from './card.js';
import * as filters from './filters.js';
import * as header from './header.js';
import * as launch from './launch.js';
import * as projects from './projects.js';
import {hideTip} from './tips.js';

const board = document.getElementById('board');

export function render() {
  hideTip();
  const tasks = store.tasks();
  const shown = filters.shown(tasks);
  projects.render();
  launch.render();
  header.render(tasks, shown.length);
  // Read off the cards themselves: the configured list can be longer than the board, and shorter than the truth.
  const manyProjects = new Set(tasks.flatMap((task) => (task.repos || []).map((repo) => repo.project))).size > 1;
  board.replaceChildren(...shown.map((task) => card(task, manyProjects)));
}

// A click carries NAMES, never a captured task, so a card rebuilt under the pointer cannot act for the task it
// used to describe.
export const onClick = ({action, report}) => {
  board.onclick = (event) => {
    const button = event.target.closest('button[data-action], button[data-report]');
    if (!button) return;
    if (button.dataset.action) action(button.dataset.task, button.dataset.action);
    else report(button.dataset.report, button.dataset.about);
  };
};
