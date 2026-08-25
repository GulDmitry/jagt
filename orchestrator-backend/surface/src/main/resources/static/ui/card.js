// One task, as one card. Pure: it reads the server's projection and builds nodes, and a button carries the two
// names a click needs (`data-task`, `data-action`) rather than a closure — the board delegates, so a card that
// was rebuilt under a pointer cannot answer for the task it used to describe.

import {link, span} from '../core/dom.js';
import {duration, countdown} from '../core/format.js';
import {blocked} from './inflight.js';

// What the unattended poller is about to do with this task, or nothing when it is not its business. The WORDS are
// the server's (watch.note), so the console and this cannot drift; only the countdown is formatted here, from the
// absolute stamp, so the slow repaint keeps it honest without another fetch.
//
// Whether the poller runs at all is a property of the install, stated once per surface, so neither shape repeats
// it: a poll that is COMING is its countdown, one that has STOPPED says what stopped. What to do instead is the
// tooltip, since the card already highlights that button.
const watchLine = (watch) => {
  if (!watch || !watch.note) return null;
  if (watch.state === 'WATCHING') {
    const remaining = watch.nextPollAt - Date.now();
    return {pulse: `next poll ${remaining <= 0 ? 'due' : countdown(remaining)}`, tip: watch.note};
  }
  return {pulse: watch.label, tip: watch.note, stalled: true};
};

// `openedAt` is 0 until a host read has said when the request opened.
const requestLink = (url, label, openedAt) => {
  const anchor = link(url, openedAt > 0 ? `${label} ${duration(Date.now() - openedAt)}` : label);
  anchor.className = 'mr-age';
  anchor.dataset.tip = openedAt > 0
    ? `review request; opened ${new Date(openedAt).toLocaleString()}`
    : 'review request; nothing has dated it yet — the next sweep will';
  return anchor;
};

// The transitions as a tooltip: the card stays one line, the record is one hover away.
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

export function card(task) {
  const owner = task.owner.toLowerCase();
  const article = document.createElement('article');
  // The edge reads the owner; the quiet tier drops its colour, so an alarm-coloured card is one that is stuck.
  article.className = task.attention === 'OPTIONAL' ? `${owner} optional` : owner;

  const top = document.createElement('div');
  top.className = 'card-top';
  top.append(span('alias', task.alias || '-'),
    task.ticketUrl ? Object.assign(link(task.ticketUrl, task.id), {className: 'id'}) : span('id', task.id));
  // Only YOUR move is news; every other owner is the status word again. The words name the ACT and the tier only
  // colours it, both the server's, so the badge, the header count and the own-move filter cannot disagree.
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
  // Two clocks, because the status one restarts on every round and on a respawned agent re-reporting itself
  // while the review keeps waiting. The activity age is not a third: on a status the agent does not own, it
  // says only that nothing has happened.
  // The age is INSIDE the status: a bare duration between two separators reads as a fact of its own.
  const status = span('status', task.statusLabel);
  status.append(' ', span('age', duration(Date.now() - task.statusSince)));
  status.dataset.tip = `${task.status}\n${timeline(task)}`;
  // One session, one or more repositories: naming them all is what tells you this task moves two codebases.
  const repos = task.repos || [];
  const where = repos.length > 1 ? repos.map((r) => r.project).join(' + ') : task.project;
  meta.append(status, span(null, where));
  // ONE stamp for several requests — the oldest — so it can be worn only where there is one request to wear
  // it. Several are named by project and ageless: the same number under each would read as each one's own.
  if (task.reviewRequestUrl && repos.length < 2) {
    meta.append(requestLink(task.reviewRequestUrl, 'MR', task.requestOpenedAt));
  } else {
    for (const repo of repos.filter((each) => each.reviewRequestUrl)) {
      meta.append(requestLink(repo.reviewRequestUrl, `${repo.project} MR`, 0));
    }
  }
  // The approval, as one dot beside the request: whether anyone has approved is what decides if this card is
  // waiting on a person or on the human reading it, and no status says so until the approval has landed.
  if (task.approved != null) {
    const approval = span(task.approved ? 'approval yes' : 'approval', '');
    approval.dataset.tip = task.approved ? 'review request approved' : 'review request not approved yet';
    meta.append(approval);
  }
  // The checks, as one dot: the sweep already reads the pipeline, and a red run while the card still says
  // CI_POLLING is the thing a status word cannot show.
  if (task.pipeline && task.pipeline !== 'NONE') {
    const checks = span(`checks ${task.pipeline.toLowerCase()}`, '');
    checks.dataset.tip = `checks: ${task.pipelineSaid || task.pipeline.toLowerCase()}`;
    meta.append(checks);
  }
  const watch = watchLine(task.autoReview);
  if (watch) {
    const pulse = span(watch.stalled ? 'pulse stalled' : 'pulse', watch.pulse);
    pulse.dataset.tip = watch.tip;
    meta.append(pulse);
  }

  const parts = [top, title, meta];

  if (task.detail) {
    const detail = document.createElement('div');
    detail.className = /^(PROBLEM|NEEDS)/.test(task.detail) ? 'detail problem' : 'detail';
    detail.textContent = task.detail;
    parts.push(detail);
  }

  // Nothing else on the page would say the drafted answers exist, and `ship` is what posts them. The line that
  // announces them is also what OPENS them: a human approving a round reads it here, not in an editor.
  if (task.draftedReplies) {
    const drafts = document.createElement('button');
    drafts.className = 'drafts';
    drafts.textContent = 'review replies drafted, not posted \u2014 read them; ship posts them';
    drafts.dataset.tip = 'every comment and the reply that will be sent for it';
    drafts.dataset.report = 'replies';
    drafts.dataset.about = task.alias || task.id;
    parts.push(drafts);
  }

  // A row per group, broken wherever the order the server sent changes it: which groups exist, and which
  // comes first, stays the projection's answer.
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
    button.disabled = blocked(task, action);
    row.append(button);
  }
  article.append(...parts);
  return article;
}
