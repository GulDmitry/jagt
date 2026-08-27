// One repaint of everything from the snapshot: no diffing, no keys. The grid is small, and a card that changes
// in place is what keeps the position a human has learnt — position carries no state here.

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
  // Read off the cards themselves: the configured list can be longer than anything on the board, and shorter
  // than the truth when a project has been taken out of the config under a task that still works in it.
  const manyProjects = new Set(tasks.flatMap((task) => (task.repos || []).map((repo) => repo.project))).size > 1;
  board.replaceChildren(...shown.map((task) => card(task, manyProjects)));
}

// ONE listener for everything a card offers, dispatched by what the button CARRIES: an action to run, or a
// report to open. A click carries NAMES, never a captured task, so a card rebuilt under the pointer cannot act
// for the task it used to describe.
export const onClick = ({action, report}) => {
  board.onclick = (event) => {
    const button = event.target.closest('button[data-action], button[data-report]');
    if (!button) return;
    if (button.dataset.action) action(button.dataset.task, button.dataset.action);
    else report(button.dataset.report, button.dataset.about);
  };
};
