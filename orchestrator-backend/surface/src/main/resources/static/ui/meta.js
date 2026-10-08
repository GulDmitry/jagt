// A card's one row of facts: the state, the request and its checks. Pure, like the card it sits on.

import {link, named, span} from '../core/dom.js';
import {duration, countdown} from '../core/format.js';

// The words are the server's; only the countdown is formatted here, so a slow repaint keeps it honest without a fetch.
const watchLine = (watch) => {
  if (!watch || !watch.note) return null;
  if (watch.state === 'WATCHING') {
    const remaining = watch.nextPollAt - Date.now();
    return {line: `next poll ${remaining <= 0 ? 'due' : countdown(remaining)}`};
  }
  return {line: `${watch.label} — ${watch.note}`, stalled: true};
};

// Equality rather than a negation: a projection missing the field would throw inside the render and blank the board.
const marked = (task) => task.pipeline === 'RED' || task.pipeline === 'RUNNING' || task.pipeline === 'GREEN';

// `sole` is false for one link among several: the approval is every repository's, not one link's.
const requestChip = (url, label, openedAt, task, sole) => {
  const anchor = link(url, openedAt > 0 ? `${label} ${duration(Date.now() - openedAt)}` : label);
  anchor.className = 'mr-age';
  const lines = [openedAt > 0
    ? `review request; opened ${new Date(openedAt).toLocaleString()}`
    : 'review request; the next sweep will date it'];
  if (sole && task.approved) {
    anchor.classList.add('approved');
    anchor.append(' ✓');
  }
  if (task.approved != null) lines.push(task.approved ? 'approved' : 'not approved yet');
  // Only where the checks have no dot to say it themselves: one verdict in two hovers can disagree with itself.
  if (!marked(task)) lines.push(`checks: ${task.pipelineSaid || 'nothing has read them yet'}`);
  const watch = watchLine(task.autoReview);
  if (watch) lines.push(watch.line);
  if (sole && watch?.stalled) anchor.classList.add('stalled');
  anchor.dataset.tip = lines.join('\n');
  return anchor;
};

// Where several links share one approval there is no label to tick, so it becomes the same glyph on its own.
const approvalTick = () => {
  const tick = named(span('tick', '✓'), 'review request approved');
  tick.dataset.tip = 'review request approved';
  return tick;
};

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

// The age is INSIDE the status: a bare duration between two separators reads as a fact of its own.
const statusChip = (task) => {
  const status = span('status', task.statusLabel);
  status.append(' ', span('age', duration(Date.now() - task.statusSince)));
  status.dataset.tip = `${task.status}\n${timeline(task)}`;
  // Where no verb on this card is the deploy, nothing else on it would say the work is live.
  if (task.deployed && !(task.actions || []).some((action) => action.again)) {
    status.classList.add('live');
    status.dataset.tip = `${status.dataset.tip}\n\nits work is on a shared branch`;
    named(status, `${task.statusLabel}, its work is on a shared branch`);
  }
  return status;
};

// Each repository named once, its request on its name: a chip beside every name would crowd the row.
const repositories = (task) => {
  const group = span('repos', '');
  task.repos.forEach((repo, index) => {
    if (index) group.append(' + ');
    group.append(repo.reviewRequestUrl
      ? requestChip(repo.reviewRequestUrl, repo.project, 0, task, false) : repo.project);
  });
  if (task.approved) group.append(' ', approvalTick());
  if (watchLine(task.autoReview)?.stalled) group.classList.add('stalled');
  return group;
};

// `manyProjects` comes from the wiring: where every card would wear the same key, it is a word nobody reads.
export function meta(task, manyProjects) {
  const row = document.createElement('div');
  row.className = 'meta';
  row.append(statusChip(task));
  if ((task.repos || []).length > 1) {
    row.append(repositories(task));
  } else {
    if (manyProjects) row.append(span(null, task.project));
    if (task.reviewRequestUrl) row.append(requestChip(task.reviewRequestUrl, 'MR', task.requestOpenedAt, task, true));
  }
  // Beside the request whether there is one link or several: the verdict is the worst repository's either way.
  if (marked(task)) row.append(checksDot(task));
  return row;
}
