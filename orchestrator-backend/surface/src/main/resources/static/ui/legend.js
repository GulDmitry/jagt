// What the board's marks mean, as a section of the help report: every row renders the page's OWN element.

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

const stalledStatusChip = () => {
  const status = span('status stalled', 'out for review');
  status.append(' ', span('age', '9d'));
  return status;
};

// The board draws this one as an anchor, and the dotted underline is half of what it says.
const requestChip = (className, text) => {
  const anchor = document.createElement('a');
  anchor.className = className;
  anchor.textContent = text;
  return anchor;
};

const repositories = () => {
  const group = span('repos', '');
  group.append(requestChip('mr-age', 'alpha'), ' + ', requestChip('mr-age', 'beta'), ' ', span('tick', '\u2713'));
  return group;
};

const deployedButton = () => {
  const button = document.createElement('button');
  button.className = 'again';
  button.textContent = 'Deploy';
  return button;
};

const flagButton = (on) => {
  const button = document.createElement('button');
  button.className = 'flag';
  button.textContent = on ? '\u2691' : '\u2690';
  button.setAttribute('aria-pressed', String(on));
  return button;
};

const offerButton = () => {
  const button = document.createElement('button');
  button.className = 'offer';
  button.textContent = 'Replies';
  return button;
};

// The report's own two marks, drawn as it draws them.
const blockHead = () => {
  const head = span('block', '1 \u00b7 ');
  head.append(span('verdict ok', 'FIXED'), ' \u00b7 thread 4');
  return head;
};

const detailLine = (className, text) => {
  const line = document.createElement('div');
  line.className = className;
  line.textContent = text;
  return line;
};

const yoursHead = () => {
  const group = document.createElement('div');
  group.className = 'group yours';
  group.append(span('group-head', 'yours 2'));
  return group;
};

const rowVerb = (className) => {
  const button = document.createElement('button');
  button.className = className;
  button.textContent = '\u25b8_';
  return button;
};

const rows = () => [
  [[edge('you'), edge('agent'), edge('ci'), edge('')],
    'your move \u00b7 agent working \u00b7 with the reviewers '
    + '\u00b7 nothing waiting, or a move of yours that can wait'],
  [[span('badge required', 'ship it'), span('badge optional', 'read the round'), span('badge required problem', 'NEEDS YOU')],
    'you are the hold-up \u00b7 yours whenever you like \u00b7 red: what is waiting on you is broken'],
  [[flagButton(false), flagButton(true)],
    'not flagged \u00b7 flagged important by you, in this browser only; its row leads its group'],
  [[statusChip(), liveStatusChip()],
    'the state and its age \u00b7 green: already on a shared branch'],
  [[deployedButton()],
    'already deployed \u2014 press again for what came after'],
  [[requestChip('id', 'ABC-42'), requestChip('mr-age approved', 'MR 5d \u2713'), requestChip('mr-age', 'MR 2d')],
    'the ticket \u00b7 the request and how long it has been open \u00b7 green \u2713: approved'],
  [[span('checks red', ''), span('checks green', ''), span('checks running', '')],
    'its checks: failed \u00b7 passed \u00b7 still running. No dot: no pipeline, or nothing read it'],
  [[repositories()],
    'a task spanning repositories: each name opens its request \u00b7 \u2713 approved'],
  [[offerButton()],
    'replies are drafted \u2014 read them before you ship'],
  [[blockHead(), span('quote', '> the row count is wrong')],
    'in a round\u2019s replies: the verdict \u2014 green fixed \u00b7 amber a question \u00b7 grey pushed back '
    + '\u2014 and the comment it answers'],
  [[requestChip('mr-age stalled', 'MR 9d'), stalledStatusChip(), span('chip stalled', 'act stopped')],
    'stopped, yours to restart: nothing will look at this round again on its own, the state wearing it where no '
    + 'request is linked · in the header, the Master only judges until you press it'],
  [[detailLine('detail problem', 'PROBLEM: no branch to ship'), detailLine('detail you', 'NEEDS YOU: a red run')],
    'the card\u2019s own line: broken \u00b7 your move'],
  [[yoursHead()],
    'in the list: the tasks waiting on you, above the rest'],
  [[span('row-title changed', 'Login form loses focus')],
    'in the list: bold, its state moved since you last had it open'],
  [[rowVerb('row-verb primary'), rowVerb('row-verb')],
    'on a row: its most pressed verb, without opening the task \u00b7 filled where that verb is your move'],
  [[span(null, '(2) jagt')],
    'the tab\u2019s title: how many tasks wait on you; its icon wears an amber dot, or red when something broke'],
  [[span('waiting', '2 need your action')],
    'in the header: how many cards are an interruption'],
  [[span('chip', 'auto-review off'), span('chip bad', '1 of 16 jobs failed'), span('chip', 'Master: act')],
    'in the header: nothing polls the requests \u00b7 a job whose last run failed, pressed for the jobs '
    + '\u00b7 the Master presses the buttons you would'],
  [[span('chip bad', 'backend unreachable')],
    'in the header: this page is cut off from the backend, and nothing on it can run'],
];

export function node() {
  const section = document.createElement('div');
  section.className = 'legend';
  section.append(span('legend-head', 'what the board’s colours and marks mean'),
    ...rows().flatMap(([samples, meaning]) => {
      const key = document.createElement('div');
      key.className = 'legend-key';
      // A sample button is a real one, and one that answers a click with nothing is worse than none.
      key.inert = true;
      key.append(...samples);
      return [key, span('legend-says', meaning)];
    }));
  return section;
}
