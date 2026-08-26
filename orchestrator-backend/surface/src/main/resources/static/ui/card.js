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

// Everything the request IS, worn by the link that names it: how long it has been open, the checks as its
// colour, an approval as a tick. Four marks side by side were four things to find and match up — and a poll
// that is merely COMING is a tooltip line, because it is never a move of yours.
//
// `openedAt` is 0 until a host read has said when the request opened. `verdicts` is false for one link among
// several: the checks are the worst repository's and the approval is all of them, so neither can ride on a
// link that names one.
const requestChip = (url, label, openedAt, task, verdicts) => {
  const anchor = link(url, openedAt > 0 ? `${label} ${duration(Date.now() - openedAt)}` : label);
  anchor.className = 'mr-age';
  const lines = [openedAt > 0
    ? `review request; opened ${new Date(openedAt).toLocaleString()}`
    : 'review request; nothing has dated it yet — the next sweep will'];
  if (verdicts) {
    if (checked(task)) {
      anchor.classList.add(task.pipeline.toLowerCase());
      lines.push(`checks: ${task.pipelineSaid || task.pipeline.toLowerCase()}`);
    }
    if (task.approved) anchor.append(' \u2713');
    if (task.approved != null) lines.push(task.approved ? 'approved' : 'not approved yet');
    const watch = watchLine(task.autoReview);
    if (watch && !watch.stalled) lines.push(watch.pulse);
  }
  anchor.dataset.tip = lines.join('\n');
  return anchor;
};

const checked = (task) => task.pipeline && task.pipeline !== 'NONE';

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

// `manyProjects` comes from the wiring: where every card would wear the same key, it is a word nobody reads.
export function card(task, manyProjects) {
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
  // The mark rides the verb it is about wherever that verb is on offer; where it is not — a task picked back
  // up, or closed — the state chip carries it, because nothing else on the card would say the work is live.
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
  // ONE stamp for several requests — the oldest — so it can be worn only where there is one request to wear
  // it. Several are named by project and ageless: the same number under each would read as each one's own.
  const folded = task.reviewRequestUrl && repos.length < 2;
  if (folded) {
    meta.append(requestChip(task.reviewRequestUrl, 'MR', task.requestOpenedAt, task, true));
  } else {
    for (const repo of repos.filter((each) => each.reviewRequestUrl)) {
      meta.append(requestChip(repo.reviewRequestUrl, `${repo.project} MR`, 0, task, false));
    }
    if (task.approved != null) {
      const approval = span(task.approved ? 'approval yes' : 'approval', '');
      approval.dataset.tip = task.approved ? 'review request approved' : 'review request not approved yet';
      meta.append(approval);
    }
    if (checked(task)) {
      const checks = span(`checks ${task.pipeline.toLowerCase()}`, '');
      checks.dataset.tip = `checks: ${task.pipelineSaid || task.pipeline.toLowerCase()}`;
      meta.append(checks);
    }
  }
  // A poll that is merely coming needs no element of its own — the link it is about carries it in the tooltip.
  // One that has STOPPED does: it hands the move back to a human, and nothing else on the card says so. Where
  // there was no one link to fold it into, it stays an element either way.
  const watch = watchLine(task.autoReview);
  if (watch && (watch.stalled || !folded)) {
    const pulse = span(watch.stalled ? 'pulse stalled' : 'pulse', watch.pulse);
    pulse.dataset.tip = watch.tip;
    meta.append(pulse);
  }

  const parts = [top, title, meta];

  if (task.detail) {
    const detail = document.createElement('div');
    // A problem is loud whatever the tier says — a broken link is broken on a card whose move can wait. What a
    // task NEEDS follows the tier: a question a poll is still reading is the human's whenever, and a line that
    // shouts on every round they have not answered yet is one they stop reading.
    const loud = /^PROBLEM/.test(task.detail)
      || (/^NEEDS/.test(task.detail) && task.attention !== 'OPTIONAL');
    detail.className = loud ? 'detail problem' : 'detail';
    detail.textContent = task.detail;
    parts.push(detail);
  }

  // Nothing else on the page would say the drafted answers exist. The line that announces them is also what
  // OPENS them, so it says so: a human approving a round reads it here, not in an editor.
  if (task.draftedReplies) {
    const drafts = document.createElement('button');
    drafts.className = 'drafts';
    drafts.textContent = 'replies drafted \u2014 click to read';
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
    // A verb whose last run is still live: the meta row is already the busiest thing on the card, so the fact
    // is worn by the control it is about rather than added beside the others.
    if (action.again) button.classList.add('again');
    button.disabled = blocked(task, action);
    row.append(button);
  }
  article.append(...parts);
  return article;
}
