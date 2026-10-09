// One repaint of everything: a card that changes in place keeps the position a human has learnt.

import * as store from '../core/store.js';
import {card} from './card.js';
import * as filters from './filters.js';
import * as header from './header.js';
import * as launch from './launch.js';
import * as order from './order.js';
import * as projects from './projects.js';
import {hideStaleTip, tipped} from './tips.js';

const board = document.getElementById('board');

export function render() {
  const tasks = store.tasks();
  const shown = order.sorted(filters.shown(tasks));
  projects.render();
  launch.render();
  header.render(tasks, shown.length);
  // Read off the cards themselves: the configured list can be longer than the board, and shorter than the truth.
  const manyProjects = new Set(tasks.flatMap((task) => (task.repos || []).map((repo) => repo.project))).size > 1;
  const read = located(tipped());
  place(shown.map((task) => card(task, manyProjects)));
  hideStaleTip(read && nodeOf(read));
}

const buttonOf = ({task, action, report, about}) => board.querySelector(action
  ? `button[data-task="${CSS.escape(task)}"][data-action="${CSS.escape(action)}"]`
  : `button[data-report="${CSS.escape(report)}"][data-about="${CSS.escape(about)}"]`);

// A chip is found again by its card and its kind, where a button is by its names.
const located = (node) => {
  const article = node?.closest('#board article');
  if (!article) return null;
  if (node.matches('button[data-action], button[data-report]')) return {button: node.dataset};
  if (!node.classList.length) return null;
  const kind = `.${CSS.escape(node.classList[0])}`;
  return {task: article.dataset.task, kind, index: [...article.querySelectorAll(kind)].indexOf(node)};
};

const nodeOf = ({button, task, kind, index}) => (button ? buttonOf(button)
  : board.querySelector(`article[data-task="${CSS.escape(task)}"]`)?.querySelectorAll(kind)[index]);

// A card that reads the same stays the same node: rebuilding it drops keyboard focus and a click in progress.
function place(fresh) {
  const focused = located(document.activeElement);
  fresh.forEach((built, index) => {
    const there = board.children[index];
    if (there?.isEqualNode(built)) return;
    if (there) there.replaceWith(built);
    else board.append(built);
  });
  while (board.children.length > fresh.length) board.lastElementChild.remove();
  if (focused && !document.activeElement?.closest('#board')) nodeOf(focused)?.focus();
}

// A press disables its own button while it runs, which drops the focus to the page; nobody else has taken it.
export function refocus(pressed) {
  if (document.activeElement === document.body) buttonOf(pressed)?.focus();
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
