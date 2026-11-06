// The legend's marks on a task: its edges, badge, state, request, checks and replies tab.

import {make, span} from '../../core/dom.js';

const edge = (owner) => span(`legend-edge ${owner}`, '');

const aged = (className, text, age) => make('span', className, text, ' ', span('age', age));

// The board draws this one as an anchor, and the dotted underline is half of what it says.
const requestChip = (className, text) => make('a', className, text);

const repositories = () => make('span', 'repos', '', requestChip('mr-age', 'alpha'), ' + ', requestChip('mr-age', 'beta'), ' ',
  span('tick', '\u2713'));

const flagButton = (on) => {
  const button = make('button', 'flag', on ? '\u2691' : '\u2690');
  button.setAttribute('aria-pressed', String(on));
  return button;
};

const draftedTab = () => {
  const tab = make('button', 'you', 'Drafted review');
  tab.setAttribute('role', 'tab');
  return make('span', 'tabs', '', tab);
};

// The report's own two marks, drawn as it draws them.
const blockHead = () => make('span', 'block', '1 \u00b7 ', span('verdict ok', 'FIXED'), ' \u00b7 thread 4');

const detailLine = (className, text) => make('div', className, text);

export const cardRows = () => [
  [[edge('you'), edge('agent'), edge('ci'), edge('')],
    'your move \u00b7 agent working \u00b7 with the reviewers '
    + '\u00b7 nothing waiting, or a move of yours that can wait'],
  [[span('badge required', 'ship it'), span('badge optional', 'read the round'), span('badge required problem', 'NEEDS YOU')],
    'you are the hold-up \u00b7 yours whenever you like \u00b7 red: what is waiting on you is broken'],
  [[flagButton(false), flagButton(true)],
    'not flagged \u00b7 flagged important by you, in this browser only; its row leads its group'],
  [[aged('status', 'in progress', '12m'), aged('status live', 'done', '3d')],
    'the state and its age \u00b7 green: already on a shared branch'],
  [[make('button', 'again', 'Deploy')],
    'already deployed \u2014 press again for what came after'],
  [[requestChip('id', 'ABC-42'), requestChip('mr-age approved', 'request 5d \u2713'), requestChip('mr-age', 'request 2d')],
    'the ticket \u00b7 the request and how long it has been open \u00b7 green \u2713: approved'],
  [[span('checks red', ''), span('checks green', ''), span('checks running', '')],
    'its checks: failed \u00b7 passed \u00b7 still running, or a retry pending. No dot: no pipeline, or nothing read it'],
  [[requestChip('mr-age', 'alpha 2d'), repositories()],
    'where the board names projects, the name opens its request; across repositories, each name its own \u00b7 \u2713 approved'],
  [[draftedTab()],
    'replies were drafted since you last opened this tab \u2014 read them before you ship'],
  [[blockHead(), span('quote', '> the row count is wrong')],
    'in a round\u2019s replies: the verdict \u2014 green fixed \u00b7 amber a question \u00b7 grey pushed back '
    + '\u2014 and the comment it answers'],
  [[requestChip('mr-age stalled', 'request 9d'), aged('status stalled', 'out for review', '9d'), span('chip stalled', 'act stopped')],
    'stopped, yours to restart: nothing will look at this round again on its own, the state wearing it where no '
    + 'request is linked · in the header, the Master only judges until you press it'],
  [[detailLine('detail problem', 'PROBLEM: no branch to ship'), detailLine('detail you', 'NEEDS YOU: a red run')],
    'the card\u2019s own line: broken \u00b7 your move'],
];
