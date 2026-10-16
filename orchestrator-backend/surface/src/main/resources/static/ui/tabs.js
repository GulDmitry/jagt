// Which tab of the open task is showing, and what that tab read: read once per task and tab, again on a change.

import {text, api} from '../core/api.js';
import * as store from '../core/store.js';

let tab = 'overview';
let read = {key: null, shown: null};

const sources = {
  session: (task) => api(`/api/tasks/screen?task=${encodeURIComponent(task.id)}`).then((answer) => answer.shown),
  round: (task) => text(`/api/commands/replies?about=${encodeURIComponent(task.alias || task.id)}`),
};

export const current = () => tab;

export const choose = (id) => {
  tab = id;
};

// What the open tab shows for `task`, null while it is being read; `repaint` runs once the read lands.
export function shown(task, repaint) {
  const source = sources[tab];
  if (!source || !task) return null;
  const key = `${tab}:${task.id}:${task.statusSince}`;
  if (read.key === key) return read.shown;
  read = {key, shown: null};
  const offeredRound = tab !== 'round' || store.offersOn(task).includes('replies');
  if (!offeredRound) return null;
  source(task).then((said) => {
    if (read.key !== key) return;
    read = {key, shown: said};
    repaint();
  }).catch((e) => {
    if (read.key !== key) return;
    read = {key, shown: `could not read it: ${e.message}`};
    repaint();
  });
  return null;
}

// A press on Refresh or a push from the backend reads the open tab again.
export const forget = () => {
  read = {key: null, shown: null};
};
