// What the board's colours and marks mean, as a section of the help report rather than a control of its own: a
// second button for "how does this work" is a second answer to the same question.
//
// Every row shows the page's OWN element beside what it means — a legend that spells a colour out in words is
// the first thing to go stale when the stylesheet moves, and a mark with no row here is one a human has to
// guess.

import {span} from '../core/dom.js';

const edge = (owner) => span(`legend-edge ${owner}`, '');

const statusChip = () => {
  const status = span('status', 'in progress');
  status.append(' ', span('age', '12m'));
  return status;
};

const liveStatusChip = () => {
  const status = span('status live', 'done');
  status.append(' ', span('age', '3d'));
  return status;
};

// The board draws this one as an anchor, and the dotted underline is half of what it says.
const requestChip = (className, text) => {
  const anchor = document.createElement('a');
  anchor.className = className;
  anchor.textContent = text;
  return anchor;
};

const deployedButton = () => {
  const button = document.createElement('button');
  button.className = 'again';
  button.textContent = 'Deploy';
  return button;
};

const draftsButton = () => {
  const button = document.createElement('button');
  button.className = 'drafts';
  button.textContent = 'replies drafted \u2014 click to read';
  return button;
};

const rows = () => [
  [[edge('you'), edge('agent'), edge('ci'), edge('')],
    'your move \u00b7 agent working \u00b7 with the reviewers \u00b7 nothing waiting'],
  [[span('badge required', 'ship it'), span('badge optional', 'read the round')],
    'you are the hold-up \u00b7 yours whenever you like'],
  [[statusChip(), liveStatusChip()],
    'the state and its age \u00b7 green: already on a shared branch'],
  [[deployedButton()],
    'already deployed \u2014 press again for what came after'],
  [[requestChip('mr-age approved', 'MR 5d \u2713'), requestChip('mr-age', 'MR 2d')],
    'the request and how long it has been open \u00b7 green \u2713: approved'],
  [[span('checks red', ''), span('checks green', ''), span('checks running', '')],
    'its checks: failed \u00b7 passed \u00b7 still running. No dot: nothing read them yet'],
  [[span('tick', '\u2713')],
    'approved, where a task spans repositories'],
  [[draftsButton()],
    'replies are drafted \u2014 read them before you ship'],
  [[span('pulse stalled', 'polling stopped')],
    'nothing will look at this round again on its own'],
  [[span('chip on', 'auto-review on'), span('chip bad', 'jobs: 1 failed')],
    'in the header: whether anything polls, and what it did'],
  [[span('dot on', ''), span('dot', '')],
    'top right: whether this page is being told about changes'],
];

export function node() {
  const section = document.createElement('div');
  section.className = 'legend';
  section.append(span('legend-head', 'what the board’s colours and marks mean'),
    ...rows().flatMap(([samples, meaning]) => {
      const key = document.createElement('div');
      key.className = 'legend-key';
      // Illustrations: a sample button is a real one, and one that answers a click with nothing is worse than
      // none — including for whoever reaches it by tabbing past Close.
      key.inert = true;
      key.append(...samples);
      return [key, span('legend-says', meaning)];
    }));
  return section;
}
