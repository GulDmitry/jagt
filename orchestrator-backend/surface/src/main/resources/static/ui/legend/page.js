// The legend's marks on the rest of the page: the list, the jobs, a verb's answer, the tab and the header.

import {make, span} from '../../core/dom.js';
import {t} from '../../core/i18n.js';

const yoursHead = () => make('div', 'group yours', '', span('group-head', `${t('list.yours')} 2`));

const verbButton = (className, text, key) => {
  const button = make('button', className, text);
  if (key) button.dataset.key = key;
  return button;
};

const failedLastRun = () => make('table', 'jobs', '',
  make('tbody', '', '', make('tr', '', '', make('td', 'last failed', t('jobs.ago', {when: '6m'})))));

const planChange = (className, text) => make('div', 'doc', '', span(className, text));

const toastEdge = (className) => make('div', className, t(className.includes('error') ? 'sample.refused' : 'sample.done'));

const rowVerb = (className) => make('button', className, '▸_');

const gearButton = () => {
  const button = document.getElementById('gear').cloneNode(true);
  button.removeAttribute('id');
  return button;
};

export const pageRows = () => [
  [yoursHead()],
  [span('row-title changed', 'Login form loses focus')],
  [planChange('changed', 'Retry the upload twice'), planChange('removed', 'Retry the upload once')],
  [rowVerb('row-verb primary'), rowVerb('row-verb')],
  [verbButton('', 'Focus', 'f'), verbButton('apart', 'Done')],
  [failedLastRun()],
  [toastEdge('toast'), toastEdge('toast error')],
  [span(null, '(2) jagt')],
  [span('waiting', t('header.waiting', {n: 2}))],
  [span('chip', t('auto-review-off')), span('chip bad', t('jobs-failed', {failing: 1, count: 16})),
    span('chip', t('header.master'))],
  [span('chip bad', t('offline')), span('chip bad', t('assistant')), span('chip bad', 'code host CLI not logged in')],
  [gearButton()],
];
