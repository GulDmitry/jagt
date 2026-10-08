// One task, as one card: it reads the server's projection and builds the nodes.

import {link, named, span} from '../core/dom.js';
import {duration, countdown} from '../core/format.js';
import {blocked} from './inflight.js';

export const DRAFTS_LABEL = 'Replies';

// Where a pasted paragraph is cut off, so no card grows taller; the whole of it stays in the hover.
const LIMIT = 150;

const clipped = (node, written) => {
  const cut = written.length > LIMIT;
  node.textContent = cut ? `${written.slice(0, LIMIT)}\u2026` : written;
  if (cut) node.dataset.tip = written;
};

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
  const tick = named(span('tick', '\u2713'), 'review request approved');
  tick.dataset.tip = 'review request approved';
  return tick;
};

// Equality rather than a negation: a projection missing the field would throw inside the render and blank the board.
const marked = (task) => task.pipeline === 'RED' || task.pipeline === 'RUNNING' || task.pipeline === 'GREEN';

const checksDot = (task) => {
  const said = `checks: ${task.pipelineSaid || task.pipeline.toLowerCase()}`;
  const dot = named(span(`checks ${task.pipeline.toLowerCase()}`, ''), said);
  dot.dataset.tip = task.pipelineUnread ? `${said}\nthe last sweep could not read them` : said;
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
  clipped(title, task.title || '');

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
    named(status, `${task.statusLabel}, its work is on a shared branch`);
  }
  meta.append(status);
  // Each repository named once, its request on its name: a chip beside every name would crowd the row.
  const repos = task.repos || [];
  if (repos.length > 1) {
    const group = span('repos', '');
    repos.forEach((repo, index) => {
      if (index) group.append(' + ');
      group.append(repo.reviewRequestUrl
        ? requestChip(repo.reviewRequestUrl, repo.project, 0, task, false) : repo.project);
    });
    if (task.approved) group.append(' ', approvalTick());
    meta.append(group);
  } else {
    if (manyProjects) meta.append(span(null, task.project));
    if (task.reviewRequestUrl) meta.append(requestChip(task.reviewRequestUrl, 'MR', task.requestOpenedAt, task, true));
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
    const yours = task.detailKind === 'YOURS' && task.attention !== 'OPTIONAL';
    detail.className = task.detailKind === 'PROBLEM' ? 'detail problem' : (yours ? 'detail you' : 'detail');
    clipped(detail, task.detail);
    parts.push(detail);
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
  // In the row that only looks, so drafted answers cost the card no height.
  if (task.draftedReplies) {
    if (!row || row.dataset.group !== 'tool') {
      row = actionRow('tool');
      parts.push(row);
    }
    const drafts = document.createElement('button');
    drafts.className = 'drafts';
    drafts.textContent = DRAFTS_LABEL;
    drafts.dataset.tip = 'replies drafted: every comment and the reply that will be sent for it';
    drafts.dataset.report = 'replies';
    drafts.dataset.about = task.alias || task.id;
    row.append(drafts);
  }
  article.append(...parts);
  return article;
}
