// One task, as one card: it reads the server's projection and builds the nodes.

import {link, named, span} from '../core/dom.js';
import * as store from '../core/store.js';
import {blocked} from './inflight.js';
import {meta} from './meta.js';

// Where a pasted paragraph is cut off, so no card grows taller; the whole of it stays in the hover.
const LIMIT = 150;

const clipped = (node, written) => {
  const cut = written.length > LIMIT;
  node.textContent = cut ? `${written.slice(0, LIMIT)}\u2026` : written;
  if (!cut) return;
  node.dataset.tip = written;
  named(node, written);
};

const actionRow = (group) => {
  const row = document.createElement('div');
  row.className = `actions ${group}`;
  row.dataset.group = group;
  return row;
};

export function card(task, manyProjects) {
  const owner = task.owner.toLowerCase();
  const article = document.createElement('article');
  article.className = task.attention === 'OPTIONAL' ? `${owner} optional` : owner;
  article.setAttribute('aria-label', store.bothNames(task));
  article.dataset.task = task.id;

  const top = document.createElement('div');
  top.className = 'card-top';
  top.append(span('alias', task.alias || '-'),
    task.ticketUrl ? Object.assign(link(task.ticketUrl, task.id), {className: 'id'}) : span('id', task.id));
  // The words name the ACT and the tier only colours it, both the server's, so badge, count and filter agree.
  if (task.ask) {
    const badge = span(`badge ${task.attention.toLowerCase()}`, task.ask);
    badge.dataset.tip = task.hint;
    badge.tabIndex = 0;
    top.append(badge);
  }

  const title = document.createElement('div');
  title.className = 'title';
  clipped(title, task.title || '');

  const parts = [top, title, meta(task, manyProjects)];

  if (task.detail) {
    const detail = document.createElement('div');
    // A problem is broken whatever the tier says; a move of theirs drops its colour with the tier.
    const yours = task.detailKind === 'YOURS' && task.attention !== 'OPTIONAL';
    detail.className = task.detailKind === 'PROBLEM' ? 'detail problem' : (yours ? 'detail you' : 'detail');
    clipped(detail, task.detail);
    parts.push(detail);
  }

  // Which groups exist, and which comes first, stays the projection's answer.
  let row = null;
  for (const action of task.actions || []) {
    if (!row || row.dataset.group !== action.group) {
      row = actionRow(action.group);
      parts.push(row);
    }
    const button = document.createElement('button');
    button.textContent = action.label;
    button.dataset.tip = action.hint;
    button.dataset.task = task.id;
    button.dataset.action = action.id;
    if (action.primary) button.className = 'primary';
    if (action.again) button.classList.add('again');
    button.disabled = blocked(task, action);
    row.append(button);
  }
  // In the row that only looks, so an offered report costs the card no height.
  for (const id of store.offersOn(task)) {
    if (!row || row.dataset.group !== 'tool') {
      row = actionRow('tool');
      parts.push(row);
    }
    const offer = document.createElement('button');
    offer.className = 'offer';
    offer.textContent = id.charAt(0).toUpperCase() + id.slice(1);
    offer.dataset.tip = store.verbs().find((verb) => verb.id === id)?.hint || id;
    offer.dataset.report = id;
    offer.dataset.about = task.alias || task.id;
    row.append(offer);
  }
  article.append(...parts);
  return article;
}
