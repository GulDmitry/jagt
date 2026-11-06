// The legend's marks on a task: its edges, badge, state, request, checks and replies tab.

import {make, span} from '../../core/dom.js';
import {t} from '../../core/i18n.js';

const edge = (owner) => span(`legend-edge ${owner}`, '');

const aged = (className, text, age) => make('span', className, text, ' ', span('age', age));

// The board draws this one as an anchor, and the dotted underline is half of what it says.
const requestChip = (className, text) => make('a', className, text);

const repositories = () => make('span', 'repos', '', requestChip('mr-age', 'alpha'), ' + ', requestChip('mr-age', 'beta'), ' ',
  span('tick', '✓'));

const flagButton = (on) => {
  const button = make('button', 'flag', on ? '⚑' : '⚐');
  button.setAttribute('aria-pressed', String(on));
  return button;
};

const draftedTab = () => {
  const tab = make('button', 'you', t('tab.round'));
  tab.setAttribute('role', 'tab');
  return make('span', 'tabs', '', tab);
};

// The report's own two marks, drawn as it draws them.
const blockHead = () => make('span', 'block', '1 · ', span('verdict ok', 'FIXED'), ' · thread 4');

const detailLine = (className, text) => make('div', className, text);

export const cardRows = () => [
  [edge('you'), edge('agent'), edge('ci'), edge('')],
  [span('badge required', 'ship it'), span('badge optional', 'read the round'), span('badge required problem', 'NEEDS YOU')],
  [flagButton(false), flagButton(true)],
  [aged('status', 'in progress', '12m'), aged('status live', 'done', '3d')],
  [make('button', 'again', 'Deploy')],
  [requestChip('id', 'ABC-42'), requestChip('mr-age approved', `${t('request')} 5d ✓`),
    requestChip('mr-age', `${t('request')} 2d`)],
  [span('checks red', ''), span('checks green', ''), span('checks running', '')],
  [requestChip('mr-age', 'alpha 2d'), repositories()],
  [draftedTab()],
  [blockHead(), span('quote', '> the row count is wrong')],
  [requestChip('mr-age stalled', `${t('request')} 9d`), aged('status stalled', 'out for review', '9d'),
    span('chip stalled', t('act-stopped'))],
  [detailLine('detail problem', 'PROBLEM: no branch to ship'), detailLine('detail you', 'NEEDS YOU: a red run')],
];
