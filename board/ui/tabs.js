
import {text, api} from '../core/api.js';
import {t} from '../core/i18n.js';
import * as store from '../core/store.js';

let tab = 'overview';
let read = {key: null, shown: null, stale: false};
let asked = 0;

const picked = new Map();

const artifacts = async (task) => {
  const id = encodeURIComponent(task.id);
  const names = await api(`/api/tasks/files?task=${id}`);
  const name = names.includes(picked.get(task.id)) ? picked.get(task.id) : names[0];
  const rendered = name ? await api(`/api/tasks/file?task=${id}&name=${encodeURIComponent(name)}`) : null;
  return {names, name, html: rendered ? rendered.html : ''};
};

let windowName = null;

// The task's session, or a window jagt opened that is no task's.
async function sessionOf(task) {
  const names = await api('/api/windows').catch(() => []);
  const name = names.includes(windowName) ? windowName : null;
  const path = name ? `/api/windows/screen?name=${encodeURIComponent(name)}`
    : `/api/tasks/screen?task=${encodeURIComponent(task.id)}`;
  const screen = await api(path).catch((e) => ({held: true, shown: t('tabs.unread', {cause: e.message})}));
  return {...screen, names, name};
}

let peeked = {};

// The Session tab reads the whole screen itself.
function glance(task, repaint) {
  if (tab === 'session') return;
  api(`/api/tasks/screen?task=${encodeURIComponent(task.id)}`).then(({held, shown: screen}) => {
    peeked = {id: task.id, held, line: screen.trimEnd().split('\n').pop().trim()};
    repaint();
  }, () => {});
}

export const peek = (task) => (peeked.id === task.id ? peeked : {});

export const pickWindow = (name) => {
  windowName = name;
  forget();
};

const sources = {
  artifacts,
  diff: (task) => api(`/api/tasks/diff?task=${encodeURIComponent(task.id)}`),
  session: sessionOf,
  round: (task, offer) => text(`/api/commands/${offer.id}?about=${encodeURIComponent(task.alias || task.id)}`),
};

export const current = () => tab;

export const pick = (task, name) => {
  picked.set(task, name);
  forget();
};

export const forget = () => {
  asked++;
  read = {...read, stale: true};
};

// A screen changes with no event, so it is read again while open.
const EVERY = 5000;
let again = () => {};

setInterval(() => again(), EVERY);

export const choose = (id) => {
  tab = id;
  if (id === 'code' && viewer?.failure) viewer = null;
  if (id === 'diff') forget();
};

let viewer = null;

export function shown(task, repaint) {
  again = () => (tab === 'session' ? (forget(), repaint()) : glance(task, repaint));
  if (peeked.id !== task.id) glance(task, repaint);
  if (tab === 'code') {
    if (!viewer) import('../viewer/Code.js').then((loaded) => { viewer = loaded; repaint(); }, (e) => { viewer = {failure: t('tabs.unread', {cause: e.message})}; repaint(); });
    return viewer;
  }
  const source = sources[tab];
  if (!source || !task) return null;
  const offer = store.offerFor(task, tab);
  if (tab === 'round' && !offer) return null;
  const key = `${tab}:${task.id}:${task.statusSince}`;
  if (read.key === key && !read.stale) return read.shown;
  // A re-read keeps the last screen up, never another task's or tab's.
  const kept = read.key === key ? read.shown : null;
  const mine = ++asked;
  read = {key, shown: kept, stale: false};
  source(task, offer).then((said) => {
    if (mine !== asked) return;
    read = {key, shown: said, stale: false};
    repaint();
  }).catch((e) => {
    if (mine !== asked) return;
    read = {key, shown: t('tabs.unread', {cause: e.message}), stale: false};
    repaint();
  });
  return kept;
}
