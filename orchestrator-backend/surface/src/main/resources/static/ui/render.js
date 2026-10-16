// One repaint of everything: a row that changes in place keeps the position a human has learnt.

import {render as paint} from 'preact';
import * as store from '../core/store.js';
import * as filters from './filters.js';
import * as flags from './flags.js';
import * as glance from './glance.js';
import * as header from './header.js';
import {html} from './html.js';
import {blocked} from './inflight.js';
import * as launch from './launch.js';
import {grouped, TaskList} from './list.js';
import * as order from './order.js';
import {Pane} from './pane.js';
import * as projects from './projects.js';
import * as seen from './seen.js';
import * as selection from './selection.js';
import {hideStaleTip} from './tips.js';

const board = document.getElementById('board');
let walked = [];

const offersOn = (task) => store.offersOn(task)
  .map((id) => ({id, hint: store.verbs().find((verb) => verb.id === id)?.hint || id}));

const blockedOn = (task) => new Set((task.actions || []).filter((action) => blocked(task, action)).map((action) => action.id));

// The tasks in the order the list shows them, which is the order the keyboard walks.
export const ordered = () => walked;

export function render() {
  const tasks = store.tasks();
  const shown = order.sorted(filters.shown(tasks));
  projects.render();
  launch.render();
  header.render(tasks, shown.length);
  // Read off the cards themselves: the configured list can be longer than the board, and shorter than the truth.
  const manyProjects = new Set(tasks.flatMap((task) => (task.repos || []).map((repo) => repo.project))).size > 1;
  const groups = grouped(shown, manyProjects);
  walked = groups.flatMap((group) => group.tasks);
  const open = selection.current(walked);
  seen.note(tasks, open);
  glance.render({waiting: tasks.filter((task) => task.attention === 'REQUIRED').length,
    broken: tasks.some((task) => task.detailKind === 'PROBLEM') || Boolean(store.jobs()?.failing) || store.actStopped()});
  board.classList.toggle('open', selection.isOpen());
  paint(open ? html`
    <${TaskList} groups=${groups} selected=${open.id} changed=${seen.changed}/>
    <${Pane} card=${{task: open, manyProjects, flagged: flags.holds(open.id), blocked: blockedOn(open),
      offers: offersOn(open)}}/>` : null, board);
  hideStaleTip(null);
}

const buttonOf = ({task, action}) => board.querySelector(
  `#pane button[data-task="${CSS.escape(task)}"][data-action="${CSS.escape(action)}"]`);

// A press disables its own button while it runs, which drops the focus to the page; nobody else has taken it.
export function refocus(pressed) {
  if (document.activeElement === document.body) buttonOf(pressed)?.focus();
}

// A click carries NAMES, never a captured task, so a row rebuilt under the pointer cannot act for the task it
// used to describe.
export const onClick = ({action, report}) => {
  board.onclick = (event) => {
    if (event.target.closest('button[data-back]')) {
      selection.back();
      render();
      return;
    }
    const picked = event.target.closest('button[data-select]');
    if (picked) {
      selection.choose(picked.dataset.select);
      render();
      return;
    }
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
