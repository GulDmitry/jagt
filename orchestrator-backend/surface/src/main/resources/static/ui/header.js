//

import {make, span} from '../core/dom.js';
import {countdown} from '../core/format.js';
import {t} from '../core/i18n.js';
import * as store from '../core/store.js';
import * as filters from './filters.js';
import * as order from './order.js';

const phaseBar = document.getElementById('phases');
const waitingLabel = document.getElementById('waiting');
const autoReviewChip = document.getElementById('auto-review');
const jobsChip = document.getElementById('jobs-pulse');
const assistantChip = document.getElementById('assistant-down');
const reachChip = document.getElementById('reach-lost');
const actChip = document.getElementById('act-stopped');
const masterChip = document.getElementById('master-mode');

const left = (due) => (due > 0 ? countdown(due) : t('due'));

// Only a failed run is news: the next one sits in the hover.
function renderJobs() {
  const jobs = store.jobs();
  jobsChip.hidden = !jobs || !jobs.failing;
  if (jobsChip.hidden) {
    return;
  }
  // A run writes no state, so this stamp is the last change's and goes into the past: past means DUE.
  const due = jobs.nextRunAt ? jobs.nextRunAt - Date.now() : null;
  jobsChip.textContent = t('jobs-failed', {failing: jobs.failing, count: jobs.count});
  jobsChip.dataset.tip = t('header.jobs.tip', {next: due === null ? '-' : left(due)});
}

// What has stopped is said; the hover names the provider's last error and when the next read probes.
function renderAssistant() {
  const down = store.assistant();
  assistantChip.hidden = !down;
  if (!down) {
    return;
  }
  const due = down.retryAt - Date.now();
  assistantChip.textContent = t('assistant');
  assistantChip.dataset.tip = t('header.assistant.tip', {cause: down.cause, next: left(due)});
}

function renderReach() {
  const lost = store.reach();
  reachChip.hidden = !lost.length;
  reachChip.disabled = !lost.some((one) => one.fix.length);
  reachChip.textContent = lost.map((one) => one.said).join(' \u00b7 ');
  reachChip.dataset.tip = lost.map((one) => one.said + (one.fix.length ? `\n${t('header.reach.fix', {fix: one.fix.join(' ')})}` : '')).join('\n')
    + `\n${t('header.reach.clears')}`;
}

// An empty board has two causes a human cannot act on the wrong one of: nothing exists yet, or controls hide
// everything. Two elements, so neither can be left showing the other's text.
function renderEmpty(held, showing) {
  document.getElementById('empty').hidden = held > 0;
  const filtered = document.getElementById('filtered');
  filtered.hidden = !(held > 0 && showing === 0);
  if (!filtered.hidden) {
    filtered.textContent = t('header.filtered', {on: filters.on(), held});
  }
}

export function render(tasks, showing) {
  const waiting = tasks.filter((task) => task.attention === 'REQUIRED').length;
  waitingLabel.hidden = waiting === 0;
  waitingLabel.textContent = t('header.waiting', {n: waiting});
  // Polling as it should is not news; only the poll being off is said.
  autoReviewChip.hidden = store.autoReview().enabled;
  autoReviewChip.textContent = store.autoReview().summary || '';
  // The Master pressing buttons for the human is said while it does; judging alone changes nothing on screen.
  masterChip.hidden = store.masterMode() !== 'act';
  masterChip.textContent = t('header.master');
  masterChip.dataset.tip = t('header.master.tip');
  renderJobs();
  renderAssistant();
  renderReach();
  const stop = store.actStop();
  actChip.hidden = !stop;
  actChip.dataset.tip = {
    STRIKES: t('header.act-stop'),
  }[stop?.cause] || '';
  renderEmpty(tasks.length, showing);
  phaseBar.hidden = tasks.length === 0;
  const perPhase = filters.narrowed(tasks);
  // The separator is CONTENT, not a gap: a line whose words only come apart when a stylesheet loads is one
  // stylesheet away from reading `build 0review 1`.
  phaseBar.replaceChildren(...store.phases().flatMap(({id: phase, label}, index) => {
    const held = perPhase.filter((task) => task.phase === phase).length;
    const segment = make('button', held ? 'phase' : 'phase empty', `${label} `, span('count', held));
    segment.disabled = held === 0;
    segment.dataset.phase = phase;
    segment.setAttribute('aria-pressed', String(filters.holds(phase)));
    segment.dataset.tip = filters.holds(phase) ? t('header.phase.off', {label}) : t('header.phase.on', {label});
    return index === 0 ? [segment] : [span('sep', ' · '), segment];
  }));
  // Another segment of the same line, separator and all: it states the order the board is IN, and the hover the
  // one a press would take it to.
  const sort = make('button', 'order', t(order.holds() ? 'header.order.alias' : 'header.order.added'));
  sort.dataset.order = '';
  sort.dataset.tip = order.holds() ? t('header.order.added.tip') : t('header.order.alias.tip');
  phaseBar.append(span('sep', ' \u00b7 '), sort);
  if (filters.on()) {
    const clear = make('button', 'clear-filters', t('header.clear', {n: filters.on()}));
    clear.dataset.clear = '';
    phaseBar.append(clear);
  }
}

export const onVerbClick = (press) => {
  [actChip, reachChip].forEach((chip) => { chip.onclick = () => press(chip.dataset.verb); });
};

export const onJobsClick = (open) => {
  jobsChip.onclick = open;
};

export const onBarClick = (repaint) => {
  phaseBar.onclick = (event) => {
    const button = event.target.closest('button');
    if (!button) return;
    if (button.dataset.phase) filters.togglePhase(button.dataset.phase);
    else if (button.hasAttribute('data-order')) order.toggle();
    else if (button.hasAttribute('data-clear')) filters.clear();
    else return;
    repaint();
  };
};
