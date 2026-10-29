// A card's one row of facts: the state, the request and its checks. Pure, like the card it sits on.

import {httpOnly} from '../core/dom.js';
import {duration, countdown} from '../core/format.js';
import {full} from '../core/time.js';
import {html} from './html.js';

// The words are the server's; only the countdown is formatted here, so a slow repaint keeps it honest without a fetch.
export const watchLine = (watch, now) => {
  if (!watch || !watch.note) return null;
  if (watch.state === 'WATCHING') {
    const remaining = watch.nextPollAt - now;
    return {line: `next poll ${remaining <= 0 ? 'due' : countdown(remaining)}`};
  }
  return {line: `${watch.label} — ${watch.note}`, stalled: true};
};

// Equality rather than a negation: a projection missing the field would throw inside the render and blank the board.
export const marked = (task) => task.pipeline === 'RED' || task.pipeline === 'RUNNING' || task.pipeline === 'GREEN';

const classes = (...names) => names.filter(Boolean).join(' ');

// `sole` is false for one link among several: the approval is every repository's, not one link's.
const RequestChip = ({url, label, openedAt, task, sole, now}) => {
  const lines = [openedAt > 0
    ? `review request; opened ${full(openedAt)}`
    : 'review request; the next sweep will date it'];
  const approved = sole && task.approved;
  if (task.approved != null) lines.push(task.approved ? 'approved' : 'not approved yet');
  // Only where the checks have no dot to say it themselves: one verdict in two hovers can disagree with itself.
  if (!marked(task)) lines.push(`checks: ${task.pipelineSaid || 'nothing has read them yet'}`);
  const watch = watchLine(task.autoReview, now);
  if (watch) lines.push(watch.line);
  const text = openedAt > 0 ? `${label} ${duration(now - openedAt)}` : label;
  return html`<a href=${httpOnly(url)} target="_blank" rel="noreferrer"
    class=${classes('mr-age', approved && 'approved', sole && watch?.stalled && 'stalled')}
    data-tip=${lines.join('\n')}>${text}${approved ? ' ✓' : ''}</a>`;
};

// Where several links share one approval there is no label to tick, so it becomes the same glyph on its own.
const ApprovalTick = () => html`<span class="tick" role="img" aria-label="review request approved" tabIndex=${0}
  data-tip="review request approved">✓</span>`;

const ChecksDot = ({task}) => {
  const said = `checks: ${task.pipelineSaid || task.pipeline.toLowerCase()}`;
  return html`<span class=${`checks ${task.pipeline.toLowerCase()}`} role="img" aria-label=${said} tabIndex=${0}
    data-tip=${task.pipelineUnread ? `${said}\nthe last sweep could not read them` : said}></span>`;
};

// The age is INSIDE the status: a bare duration between two separators reads as a fact of its own.
const StatusChip = ({task, stalled, now}) => {
  let tip = stalled ? stalled.line : '';
  if (task.waiting) tip = tip ? `${tip}\n\n${task.waiting}` : task.waiting;
  // Where no verb on this card is the deploy, nothing else on it would say the work is live.
  const live = task.deployed && !(task.actions || []).some((action) => action.again);
  if (live) tip = tip ? `${tip}\n\nits work is on a shared branch` : 'its work is on a shared branch';
  return html`<span class=${classes('status', stalled && 'stalled', live && 'live')} data-tip=${tip || undefined} tabIndex=${tip ? 0 : undefined}
    role=${live ? 'img' : undefined} aria-label=${live ? `${task.statusLabel}, its work is on a shared branch` : undefined}
    >${task.statusLabel} <span class="age">${duration(now - task.statusSince)}</span></span>`;
};

// Each repository named once, its request on its name: a chip beside every name would crowd the row.
const Repositories = ({task, now}) => html`<span class=${classes('repos', watchLine(task.autoReview, now)?.stalled && 'stalled')}>${
  task.repos.map((repo, index) => html`${index ? ' + ' : ''}${repo.reviewRequestUrl
    ? html`<${RequestChip} url=${repo.reviewRequestUrl} label=${repo.project} openedAt=${0} task=${task} sole=${false} now=${now}/>`
    : repo.project}`)}${task.approved ? html` <${ApprovalTick}/>` : ''}</span>`;

// `manyProjects` comes from the wiring: where every card would wear the same key, it is a word nobody reads.
export function Meta({task, manyProjects, now}) {
  const many = (task.repos || []).length > 1;
  // A stopped poll with no request chip to ring rings the state instead.
  const watch = watchLine(task.autoReview, now);
  return html`<div class="meta">
    <${StatusChip} task=${task} now=${now} stalled=${!many && !task.reviewRequestUrl && watch?.stalled ? watch : null}/>
    ${many ? html`<${Repositories} task=${task} now=${now}/>` : html`
      ${manyProjects ? html`<span>${task.project}</span>` : ''}
      ${task.reviewRequestUrl ? html`<${RequestChip} url=${task.reviewRequestUrl} label="MR"
        openedAt=${task.requestOpenedAt} task=${task} sole=${true} now=${now}/>` : ''}`}
    ${marked(task) ? html`<${ChecksDot} task=${task}/>` : ''}
  </div>`;
}
