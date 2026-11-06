// The legend's marks on the rest of the page: the list, the jobs, a verb's answer, the tab and the header.

import {make, span} from '../../core/dom.js';

const yoursHead = () => make('div', 'group yours', '', span('group-head', 'yours 2'));

const verbButton = (className, text, key) => {
  const button = make('button', className, text);
  if (key) button.dataset.key = key;
  return button;
};

const failedLastRun = () => make('table', 'jobs', '',
  make('tbody', '', '', make('tr', '', '', make('td', 'last failed', '6m ago'))));

const planChange = (className, text) => make('div', 'doc', '', span(className, text));

const toastEdge = (className) => make('div', className, className.includes('error') ? 'refused' : 'done');

const rowVerb = (className) => make('button', className, '\u25b8_');

const gearButton = () => {
  const button = document.getElementById('gear').cloneNode(true);
  button.removeAttribute('id');
  return button;
};

export const pageRows = () => [
  [[yoursHead()],
    'in the list: the tasks waiting on you, above the rest'],
  [[span('row-title changed', 'Login form loses focus')],
    'in the list: bold, its state moved since you last had it open'],
  [[planChange('changed', 'Retry the upload twice'), planChange('removed', 'Retry the upload once')],
    'in a plan: bold with a bar, new or changed since the plan you last approved \u00b7 struck through, removed since'],
  [[rowVerb('row-verb primary'), rowVerb('row-verb')],
    'on a row: its most pressed verb, without opening the task \u00b7 filled where that verb is your move'],
  [[verbButton('', 'Focus', 'f'), verbButton('apart', 'Done')],
    'a verb\u2019s key, drawn after its name \u00b7 Done muted at the row\u2019s end until hovered'],
  [[failedLastRun()],
    'in the jobs: the last run failed, its age in red \u00b7 its dot and a path check\u2019s are the checks dot'],
  [[toastEdge('toast'), toastEdge('toast error')],
    'a verb\u2019s answer, passing: it ran \u00b7 it was refused'],
  [[span(null, '(2) jagt')],
    'the tab\u2019s title: how many tasks wait on you; its icon wears an amber dot, or red when something broke'],
  [[span('waiting', '2 need your action')],
    'in the header: how many cards are an interruption'],
  [[span('chip', 'auto-review off'), span('chip bad', '1 of 16 jobs failed'), span('chip', 'Master: act')],
    'in the header: nothing polls the requests \u00b7 a job whose last run failed, pressed for the jobs '
    + '\u00b7 the Master presses the buttons you would'],
  [[span('chip bad', 'backend unreachable'), span('chip bad', 'assistant unavailable'),
    span('chip bad', 'glab not logged in to host')],
    'in the header: this page is cut off from the backend, and nothing on it can run \u00b7 the model provider '
    + 'failed repeatedly, so headless reads wait for one probe \u00b7 jagt cannot reach a code host, pressed to log in, no card stops'],
  [[gearButton()],
    'in the header: the gear opens the settings \u00b7 the theme, auto following the system, kept in this browser alone'],
];
