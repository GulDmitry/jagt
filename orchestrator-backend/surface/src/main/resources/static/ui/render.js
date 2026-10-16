// One repaint of everything: a card that changes in place keeps the position a human has learnt.

import {render as paint} from 'preact';
import * as store from '../core/store.js';
import {Card} from './card.js';
import * as filters from './filters.js';
import * as flags from './flags.js';
import * as header from './header.js';
import {html} from './html.js';
import {blocked} from './inflight.js';
import * as launch from './launch.js';
import * as order from './order.js';
import * as projects from './projects.js';
import {hideStaleTip} from './tips.js';

const board = document.getElementById('board');

const offersOn = (task) => store.offersOn(task)
  .map((id) => ({id, hint: store.verbs().find((verb) => verb.id === id)?.hint || id}));

const blockedOn = (task) => new Set((task.actions || []).filter((action) => blocked(task, action)).map((action) => action.id));

export function render() {
  const tasks = store.tasks();
  const shown = order.sorted(filters.shown(tasks));
  projects.render();
  launch.render();
  header.render(tasks, shown.length);
  // Read off the cards themselves: the configured list can be longer than the board, and shorter than the truth.
  const manyProjects = new Set(tasks.flatMap((task) => (task.repos || []).map((repo) => repo.project))).size > 1;
  // Keyed by task, so a card that reads the same keeps its node, its keyboard focus and a click in progress.
  paint(shown.map((task) => html`<${Card} key=${task.id} task=${task} manyProjects=${manyProjects}
    flagged=${flags.holds(task.id)} blocked=${blockedOn(task)} offers=${offersOn(task)}/>`), board);
  hideStaleTip(null);
}

const buttonOf = ({task, action}) => board.querySelector(
  `button[data-task="${CSS.escape(task)}"][data-action="${CSS.escape(action)}"]`);

// A press disables its own button while it runs, which drops the focus to the page; nobody else has taken it.
export function refocus(pressed) {
  if (document.activeElement === document.body) buttonOf(pressed)?.focus();
}

// A click carries NAMES, never a captured task, so a card rebuilt under the pointer cannot act for the task it
// used to describe.
export const onClick = ({action, report}) => {
  board.onclick = (event) => {
    const flagged = event.target.closest('button[data-flag]');
    if (flagged) {
      flags.toggle(flagged.dataset.flag);
      render();
      return;
    }
    const button = event.target.closest('button[data-action], button[data-report]');
    if (!button) return;
    if (button.dataset.action) action(button.dataset.task, button.dataset.action);
    else report(button.dataset.report, button.dataset.about);
  };
};
