// The one line above the grid: whose move it is, what the install polls, what the jobs are doing, and the phase
// counts. Every count is a NUMBER in a line that never moves — a phase that owned a column would have to move
// the card it describes, and re-finding it is the cost a human pays for the arrangement.
//
// Pure rendering: the phase buttons carry `data-phase`, and the click is delegated through `onNarrow`, because
// what narrowing MEANS on screen is the render's answer, not this module's.

import {span} from '../core/dom.js';
import {countdown} from '../core/format.js';
import * as store from '../core/store.js';
import * as filters from './filters.js';

const PHASES = [
  ['BUILD', 'build'], ['REVIEW', 'review'], ['CHECK', 'check'],
  ['READY', 'ready'], ['DEPLOY', 'deploy'], ['DONE', 'done'],
];

const phaseBar = document.getElementById('phases');
const waitingLabel = document.getElementById('waiting');
const autoReviewChip = document.getElementById('auto-review');
const jobsChip = document.getElementById('jobs-pulse');

// A failed run OUTRANKS the countdown: the next run is not news while the last one is still broken.
function renderJobs() {
  const jobs = store.jobs();
  jobsChip.hidden = !jobs || !jobs.count;
  if (jobsChip.hidden) {
    return;
  }
  // A run writes no state, so nothing pushes a fresh stamp here: this one is from the last state change and
  // goes into the past within the minute. Past means DUE, not `0s` — a countdown frozen at zero reads as broken.
  const due = jobs.nextRunAt ? jobs.nextRunAt - Date.now() : null;
  jobsChip.textContent = jobs.failing
    ? `jobs: ${jobs.failing} failed`
    : `jobs: ${due === null ? 'next -' : due > 0 ? `next ${countdown(due)}` : 'due'}`;
  jobsChip.classList.toggle('bad', jobs.failing > 0);
  jobsChip.dataset.tip = jobs.failing
    ? 'a job\u2019s last run failed; open the Jobs report'
    : 'next scheduled run of any unattended job; the ticker runs every minute';
}

// An empty board has two causes and a human cannot act on the wrong one: nothing exists yet, or everything is
// hidden by controls they may not be looking at. Two elements rather than one message rewritten in place, so
// neither can be left showing the other's text.
function renderEmpty(held, showing) {
  document.getElementById('empty').hidden = held > 0;
  const filtered = document.getElementById('filtered');
  filtered.hidden = !(held > 0 && showing === 0);
  if (!filtered.hidden) {
    filtered.textContent = `No task matches: ${filters.on()} filter(s) on, ${held} task(s) hidden.`;
  }
}

export function render(tasks, showing) {
  const waiting = tasks.filter((task) => task.owner === 'YOU').length;
  waitingLabel.hidden = waiting === 0;
  waitingLabel.textContent = `${waiting} need your action`;
  autoReviewChip.textContent = store.autoReview().summary || '';
  autoReviewChip.classList.toggle('on', store.autoReview().enabled);
  renderJobs();
  renderEmpty(tasks.length, showing);
  phaseBar.hidden = tasks.length === 0;
  const perPhase = filters.narrowed(tasks);
  // The separator is CONTENT, not a gap: a line whose words only come apart when a stylesheet loads is one
  // stylesheet away from reading `build 0review 1`.
  phaseBar.replaceChildren(...PHASES.flatMap(([phase, label], index) => {
    const held = perPhase.filter((task) => task.phase === phase).length;
    const segment = document.createElement('button');
    segment.className = held ? 'phase' : 'phase empty';
    segment.append(`${label} `, span('count', held));
    // Nothing to show is nothing to press, and an empty phase says so rather than answering with a blank board.
    segment.disabled = held === 0;
    segment.dataset.phase = phase;
    segment.setAttribute('aria-pressed', String(filters.holds(phase)));
    segment.dataset.tip = filters.holds(phase) ? `stop showing only ${label}` : `show only ${label}`;
    return index === 0 ? [segment] : [span('sep', ' · '), segment];
  }));
  if (filters.on()) {
    const clear = document.createElement('button');
    clear.className = 'clear-filters';
    clear.dataset.clear = '';
    clear.textContent = `clear ${filters.on()} filter(s)`;
    phaseBar.append(clear);
  }
}

export const onNarrow = (repaint) => {
  phaseBar.onclick = (event) => {
    const button = event.target.closest('button');
    if (!button) return;
    if (button.dataset.phase) filters.togglePhase(button.dataset.phase);
    else if (button.hasAttribute('data-clear')) filters.clear();
    else return;
    repaint();
  };
};
