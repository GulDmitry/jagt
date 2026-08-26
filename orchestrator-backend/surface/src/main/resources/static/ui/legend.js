// What the board's colours and marks mean, as a section of the help report rather than a control of its own: a
// second button for "how does this work" is a second answer to the same question.
//
// Every row shows the page's OWN element beside what it means — a legend that spells a colour out in words is
// the first thing to go stale when the stylesheet moves. Board-only, the way `quit` is console-only: the console
// has none of these marks and says every one of the facts in words already.

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

const rows = () => [
  [[deployedButton()],
    'a verb whose last run is still live — this task’s work is on a shared branch already, so pressing it '
    + 'deploys what has been added since. `revert` takes it back out and the colour with it'],
  [[edge('you'), edge('agent'), edge('ci'), edge('')],
    'a card’s left edge — your move, the agent working, out with the reviewers, nothing waiting'],
  [[span('badge required', 'ship it'), span('badge optional', 'read the round')],
    'what to do while it is yours — filled: you are the hold-up; grey: whenever you like'],
  [[statusChip(), liveStatusChip()],
    'the state, and how long it has been in THIS one. Green where no verb on the card is the deploy: the work '
    + 'is on a shared branch already'],
  [[requestChip('mr-age approved', 'MR 5d \u2713'), requestChip('mr-age', 'MR 2d')],
    'the review request and how long it has been open — approved, and plain until somebody has. A request '
    + 'nobody has read yet looks the same as an unapproved one: hover for all of it in words, and for when '
    + 'the next unattended poll is due'],
  [[span('checks red', ''), span('checks running', '')],
    'beside it, what its checks did — failed, still running. A run that passed has no dot: it is the state '
    + 'everyone expects'],
  [[span('approval', ''), span('approval yes', '')],
    'only where a task spans repositories, and one link cannot carry an approval that is all of them: nobody '
    + 'has approved yet — approved'],
  [[span('pulse stalled', 'polling stopped')],
    'nothing will look at this round again on its own — the next move is a person’s'],
  [[span('chip on', 'auto-review on'), span('chip bad', 'jobs: 1 failed')],
    'in the header — whether anything polls at all, and what the unattended runs are doing'],
  [[span('dot on', ''), span('dot', '')],
    'top right — the push connection. Grey: this page is not being told about changes'],
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
