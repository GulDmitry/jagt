// Which tab of the open task is showing, and what that tab read: read once per task and tab, again on a change.

import {text, api} from '../core/api.js';
import * as store from '../core/store.js';

let tab = 'overview';
let read = {key: null, shown: null, stale: false};
let asked = 0;

const sources = {
  session: (task) => api(`/api/tasks/screen?task=${encodeURIComponent(task.id)}`)
    .catch((e) => ({held: true, shown: `could not read it: ${e.message}`})),
  round: (task) => text(`/api/commands/replies?about=${encodeURIComponent(task.alias || task.id)}`),
};

export const current = () => tab;

// A press on Refresh, a push from the backend or the tick reads the open tab again.
export const forget = () => {
  asked++;
  read = {...read, stale: true};
};

// A session's screen changes with no event to say so, so its tab reads it again while it stays open.
const EVERY = 5000;
let ticking = 0;
let repaintOnTick = () => {};

export const choose = (id) => {
  tab = id;
  clearInterval(ticking);
  ticking = id === 'session' ? setInterval(() => {
    forget();
    repaintOnTick();
  }, EVERY) : 0;
};

// What the open tab shows for `task`, null while it is being read; `repaint` runs once the read lands.
export function shown(task, repaint) {
  repaintOnTick = repaint;
  const source = sources[tab];
  if (!source || !task) return null;
  if (tab === 'round' && !store.offersOn(task).includes('replies')) return null;
  const key = `${tab}:${task.id}:${task.statusSince}`;
  if (read.key === key && !read.stale) return read.shown;
  // A re-read keeps the last screen up rather than flashing back to "reading…", but never another task's or tab's.
  const kept = read.key === key ? read.shown : null;
  const mine = ++asked;
  read = {key, shown: kept, stale: false};
  source(task).then((said) => {
    if (mine !== asked) return;
    read = {key, shown: said, stale: false};
    repaint();
  }).catch((e) => {
    if (mine !== asked) return;
    read = {key, shown: `could not read it: ${e.message}`, stale: false};
    repaint();
  });
  return kept;
}
