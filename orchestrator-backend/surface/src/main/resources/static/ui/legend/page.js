// The legend's marks on the rest of the page: the list, the jobs, a verb's answer, the tab and the header.

import {span} from '../../core/dom.js';

const yoursHead = () => {
  const group = document.createElement('div');
  group.className = 'group yours';
  group.append(span('group-head', 'yours 2'));
  return group;
};

const verbButton = (className, text, key) => {
  const button = document.createElement('button');
  button.className = className;
  button.textContent = text;
  if (key) button.dataset.key = key;
  return button;
};

const failedLastRun = () => {
  const table = document.createElement('table');
  table.className = 'jobs';
  const cell = document.createElement('td');
  cell.className = 'last failed';
  cell.textContent = '6m ago';
  const row = document.createElement('tr');
  row.append(cell);
  const body = document.createElement('tbody');
  body.append(row);
  table.append(body);
  return table;
};

const toastEdge = (className) => {
  const toast = document.createElement('div');
  toast.className = className;
  toast.textContent = className.includes('error') ? 'refused' : 'done';
  return toast;
};

const rowVerb = (className) => {
  const button = document.createElement('button');
  button.className = className;
  button.textContent = '\u25b8_';
  return button;
};

export const pageRows = () => [
  [[yoursHead()],
    'in the list: the tasks waiting on you, above the rest'],
  [[span('row-title changed', 'Login form loses focus')],
    'in the list: bold, its state moved since you last had it open'],
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
  [[span('chip bad', 'backend unreachable'), span('chip bad', 'assistant unavailable')],
    'in the header: this page is cut off from the backend, and nothing on it can run \u00b7 the model provider '
    + 'failed repeatedly, so headless reads wait for one probe'],
];
