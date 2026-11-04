// One task, as one card: every fact arrives in its props, so a card renders the same wherever it is drawn.

import {bothNames, stopLines, switchLines} from '../core/format.js';
import {httpOnly} from '../core/dom.js';
import {html} from './html.js';
import {Meta} from './Meta.js';

const Flag = ({taskId, on}) => html`<button class="flag" data-flag=${taskId} aria-pressed=${String(on)}
  aria-label="flag as important" data-tip="important — kept in this browser only">${on ? '⚑' : '⚐'}</button>`;

// A verb drawn apart stays muted at the row's end even where the move names it.
const verbClass = (action) => [action.primary && !action.apart && 'primary', action.again && 'again', action.apart && 'apart']
  .filter(Boolean).join(' ') || undefined;

// Which groups exist, and which comes first, stays the projection's answer; a verb drawn apart ends its row.
const rows = (task) => {
  const grouped = [];
  for (const action of task.actions || []) {
    if (grouped.at(-1)?.group !== action.group) grouped.push({group: action.group, items: []});
    grouped.at(-1).items.push({action});
  }
  const last = (item) => Number(Boolean(item.action.apart));
  grouped.forEach((row) => row.items.sort((one, other) => last(one) - last(other)));
  return grouped;
};

const Button = ({task, action, blocked}) => html`<button class=${verbClass(action)} data-tip=${action.hint}
  data-task=${task.id} data-action=${action.id} disabled=${blocked.has(action.id)} data-key=${action.key || undefined}
  aria-label=${action.key ? action.label : undefined}
  >${action.label}</button>`;

// A problem is broken whatever the tier says; a move of theirs drops its colour with the tier.
const detailClass = (task) => {
  if (task.detailKind === 'PROBLEM') return 'detail problem';
  return task.detailKind === 'YOURS' && task.attention !== 'OPTIONAL' ? 'detail you' : 'detail';
};

// `blocked` holds the ids of the verbs a press in flight refuses; `now` the clock every age on it is read against.
export function Card({task, manyProjects, flagged, blocked, now}) {
  const owner = task.owner.toLowerCase();
  const ticket = httpOnly(task.ticketUrl);
  // The badge's words name the ACT and the tier only colours it, both the server's, so badge, count and filter agree.
  return html`<article class=${task.attention === 'OPTIONAL' ? `${owner} optional` : owner}
      aria-label=${bothNames(task)} data-task=${task.id}>
    <div class="card-top">
      <span class="alias">${task.alias || '-'}</span>
      ${task.ticketUrl
        ? html`<a href=${ticket} target="_blank" rel="noreferrer" class="id">${task.id}</a>`
        : html`<span class="id">${task.id}</span>`}
      ${task.ask ? html`<span class=${`badge ${task.attention.toLowerCase()}${task.detailKind === 'PROBLEM' ? ' problem' : ''}`} data-tip=${[task.hint, ...stopLines(task)].join('\n')}
        tabIndex=${0}>${task.ask}</span>` : ''}
    </div>
    <div class="heading"><${Flag} taskId=${task.id} on=${flagged}/><div class="title" data-tip=${switchLines(task).join('\n') || undefined}>${task.title || ''}</div></div>
    <${Meta} task=${task} manyProjects=${manyProjects} now=${now}/>
    ${task.detail ? html`<div class=${detailClass(task)}>${task.detail}</div>` : ''}
    ${rows(task).map(({group, items}) => html`<div class=${`actions ${group}`} data-group=${group}>${
      items.map(({action}) => html`<${Button} task=${task} action=${action} blocked=${blocked}/>`)}</div>`)}
  </article>`;
}
