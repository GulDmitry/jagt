// One task, as one card: it reads the server's projection and builds the nodes.

import {link, span} from '../core/dom.js';
import {duration, countdown} from '../core/format.js';
import {blocked} from './inflight.js';

export const DRAFTS_LABEL = 'replies drafted \u2014 click to read';

// The words are the server's; only the countdown is formatted here, so a slow repaint keeps it honest without a fetch.
const watchLine = (watch) => {
  if (!watch || !watch.note) return null;
  if (watch.state === 'WATCHING') {
    const remaining = watch.nextPollAt - Date.now();
    return {pulse: `next poll ${remaining <= 0 ? 'due' : countdown(remaining)}`, tip: watch.note};
  }
  return {pulse: watch.label, tip: watch.note, stalled: true};
};

// `sole` is false for one link among several: the approval is every repository's, not one link's.
const requestChip = (url, label, openedAt, task, sole) => {
  const anchor = link(url, openedAt > 0 ? `${label} ${duration(Date.now() - openedAt)}` : label);
  anchor.className = 'mr-age';
  const lines = [openedAt > 0
    ? `review request; opened ${new Date(openedAt).toLocaleString()}`
    : 'review request; the next sweep will date it'];
  if (sole && task.approved) {
    anchor.classList.add('approved');
    anchor.append(' \u2713');
  }
  if (task.approved != null) lines.push(task.approved ? 'approved' : 'not approved yet');
  // Only where the checks have no dot to say it themselves: one verdict in two hovers can disagree with itself.
  if (!marked(task)) lines.push(`checks: ${task.pipelineSaid || 'nothing has read them yet'}`);
  const watch = watchLine(task.autoReview);
  if (watch && !watch.stalled) lines.push(watch.pulse);
  anchor.dataset.tip = lines.join('\n');
  return anchor;
};

// Where several links share one approval there is no label to tick, so it becomes the same glyph on its own.
const approvalTick = () => {
  const tick = span('tick', '\u2713');
  tick.dataset.tip = 'review request approved';
  return tick;
};

// Equality rather than a negation: a projection missing the field would throw inside the render and blank the board.
const marked = (task) => task.pipeline === 'RED' || task.pipeline === 'RUNNING' || task.pipeline === 'GREEN';

const checksDot = (task) => {
  const dot = span(`checks ${task.pipeline.toLowerCase()}`, '');
  dot.dataset.tip = `checks: ${task.pipelineSaid || task.pipeline.toLowerCase()}`;
  return dot;
};

const timeline = (task) => (task.history || [])
  .map((step) => {
    const asked = step.origin ? `  (${step.origin.toLowerCase().replace('_', '-')})` : '';
    return `${new Date(step.at).toLocaleString()}  ${step.status}${asked}`;
  })
  .join('\n');

const actionRow = (group) => {
  const row = document.createElement('div');
  row.className = `actions ${group}`;
  row.dataset.group = group;
  return row;
};

// `manyProjects` comes from the wiring: where every card would wear the same key, it is a word nobody reads.
export function card(task, manyProjects) {
  const owner = task.owner.toLowerCase();
  const article = document.createElement('article');
  article.className = task.attention === 'OPTIONAL' ? `${owner} optional` : owner;

  const top = document.createElement('div');
  top.className = 'card-top';
  top.append(span('alias', task.alias || '-'),
    task.ticketUrl ? Object.assign(link(task.ticketUrl, task.id), {className: 'id'}) : span('id', task.id));
  // The words name the ACT and the tier only colours it, both the server's, so badge, count and filter agree.
  if (task.ask) {
    const badge = span(`badge ${task.attention.toLowerCase()}`, task.ask);
    badge.dataset.tip = task.hint;
    top.append(badge);
  }

  const title = document.createElement('div');
  title.className = 'title';
  title.textContent = task.title || '';

  const meta = document.createElement('div');
  meta.className = 'meta';
  // The age is INSIDE the status: a bare duration between two separators reads as a fact of its own.
  const status = span('status', task.statusLabel);
  status.append(' ', span('age', duration(Date.now() - task.statusSince)));
  status.dataset.tip = `${task.status}\n${timeline(task)}`;
  // Where no verb on this card is the deploy, nothing else on it would say the work is live.
  if (task.deployed && !task.actions.some((action) => action.again)) {
    status.classList.add('live');
    status.dataset.tip = `${status.dataset.tip}\n\nits work is on a shared branch`;
  }
  meta.append(status);
  // One session, one or more repositories: naming them all is what tells you this task moves two codebases.
  const repos = task.repos || [];
  if (manyProjects || repos.length > 1) {
    meta.append(span(null, repos.length > 1 ? repos.map((r) => r.project).join(' + ') : task.project));
  }
  // ONE stamp for several requests: the same number under each would read as each one's own.
  const folded = task.reviewRequestUrl && repos.length < 2;
  if (folded) {
    meta.append(requestChip(task.reviewRequestUrl, 'MR', task.requestOpenedAt, task, true));
  } else {
    for (const repo of repos.filter((each) => each.reviewRequestUrl)) {
      meta.append(requestChip(repo.reviewRequestUrl, `${repo.project} MR`, 0, task, false));
    }
    if (task.approved) meta.append(approvalTick());
  }
  // Beside the request whether there is one link or several: the verdict is the worst repository's either way.
  if (marked(task)) meta.append(checksDot(task));
  // Only a poll that has STOPPED earns an element: it hands the move back, and nothing else on the card says so.
  const watch = watchLine(task.autoReview);
  if (watch && watch.stalled) {
    const pulse = span('pulse stalled', watch.pulse);
    pulse.dataset.tip = watch.tip;
    meta.append(pulse);
  }

  const parts = [top, title, meta];

  if (task.detail) {
    const detail = document.createElement('div');
    // A problem is broken whatever the tier says; a move of theirs drops its colour with the tier.
    const yours = /^NEEDS/.test(task.detail) && task.attention !== 'OPTIONAL';
    detail.className = /^PROBLEM/.test(task.detail) ? 'detail problem' : (yours ? 'detail you' : 'detail');
    detail.textContent = task.detail;
    parts.push(detail);
  }

  // Nothing else on the page would say the drafted answers exist.
  if (task.draftedReplies) {
    const drafts = document.createElement('button');
    drafts.className = 'drafts';
    drafts.textContent = DRAFTS_LABEL;
    drafts.dataset.tip = 'every comment and the reply that will be sent for it';
    drafts.dataset.report = 'replies';
    drafts.dataset.about = task.alias || task.id;
    parts.push(drafts);
  }

  // Which groups exist, and which comes first, stays the projection's answer.
  let row = null;
  for (const action of task.actions) {
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
  article.append(...parts);
  return article;
}
