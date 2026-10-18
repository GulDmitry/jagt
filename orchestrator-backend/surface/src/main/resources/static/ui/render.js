// One repaint of everything: a row that changes in place keeps the position a human has learnt.

import {render as paint} from 'preact';
import * as store from '../core/store.js';
import * as filters from './filters.js';
import * as flags from './flags.js';
import * as glance from './glance.js';
import * as header from './header.js';
import {html} from '../components/html.js';
import {blocked} from './inflight.js';
import * as launch from './launch.js';
import {grouped, TaskList} from '../components/TaskList.js';
import * as order from './order.js';
import {Pane} from '../components/Pane.js';
import * as projects from './projects.js';
import * as seen from './seen.js';
import * as selection from './selection.js';
import * as tabs from './tabs.js';
import {sayTo} from '../core/api.js';
import {sending} from './submit.js';
import {said} from './keys.js';
import {hideStaleTip} from './tips.js';

const board = document.getElementById('board');
let walked = [];

const offersOn = (task) => store.offersOn(task).map((id) => {
  const verb = store.verbs().find((each) => each.id === id);
  return {id, hint: verb?.hint || id, part: verb?.part};
});

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
  const groups = grouped(shown, manyProjects, (task) => flags.holds(task.id));
  walked = groups.flatMap((group) => group.tasks);
  const open = selection.current(walked);
  const now = Date.now();
  // Wide, the open task stands on screen whether picked or not, so it is seen; narrow, only once opened.
  seen.note(tasks, selection.paneShown() ? open : null);
  glance.render({waiting: tasks.filter((task) => task.attention === 'REQUIRED').length,
    broken: tasks.some((task) => task.detailKind === 'PROBLEM') || Boolean(store.jobs()?.failing) || store.actStopped()});
  board.classList.toggle('open', selection.isOpen());
  const rowVerb = shown.flatMap((task) => task.actions || []).find((action) => action.onRow);
  paint(open ? html`
    <${TaskList} groups=${groups} selected=${open.id} changed=${seen.changed} now=${now} keys=${said(rowVerb)}/>
    <${Pane} card=${{task: open, manyProjects, flagged: flags.holds(open.id), blocked: blockedOn(open),
      offers: offersOn(open), now}} tab=${tabs.current()} shown=${tabs.shown(open, render)}/>` : null, board);
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
    const tab = event.target.closest('button[data-tab]');
    if (tab) {
      tabs.choose(tab.dataset.tab);
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

// The line typed under an open session goes into that session, as the say line of a report does.
board.addEventListener('submit', (event) => {
  const form = event.target.closest('form[data-say]');
  if (!form) return;
  event.preventDefault();
  const line = form.elements.line.value.trim();
  if (!line) return;
  sending(form, {waiting: 'sending…', send: () => sayTo(form.dataset.say, line), done: () => {
    form.reset();
    tabs.forget();
    render();
  }});
});
