// One task, as one card: every fact arrives in its props, so a card renders the same wherever it is drawn.

import {bothNames} from '../core/store.js';
import {httpOnly} from '../core/dom.js';
import {html} from './html.js';
import {Meta} from './meta.js';

const Flag = ({taskId, on}) => html`<button class="flag" data-flag=${taskId} aria-pressed=${String(on)}
  aria-label="flag as important" data-tip="important — kept in this browser only">${on ? '⚑' : '⚐'}</button>`;

const verbClass = (action) => [action.primary && 'primary', action.again && 'again'].filter(Boolean).join(' ') || undefined;

// Which groups exist, and which comes first, stays the projection's answer; an offered report joins the row that only looks.
const rows = (task, offers) => {
  const grouped = [];
  for (const action of task.actions || []) {
    if (grouped.at(-1)?.group !== action.group) grouped.push({group: action.group, items: []});
    grouped.at(-1).items.push({action});
  }
  for (const offer of offers) {
    if (grouped.at(-1)?.group !== 'tool') grouped.push({group: 'tool', items: []});
    grouped.at(-1).items.push({offer});
  }
  return grouped;
};

const Button = ({task, action, blocked}) => html`<button class=${verbClass(action)} data-tip=${action.hint}
  data-task=${task.id} data-action=${action.id} disabled=${blocked.has(action.id)}>${action.label}</button>`;

const Offer = ({task, offer}) => html`<button class="offer" data-tip=${offer.hint} data-report=${offer.id}
  data-about=${task.alias || task.id}>${offer.id.charAt(0).toUpperCase() + offer.id.slice(1)}</button>`;

// A problem is broken whatever the tier says; a move of theirs drops its colour with the tier.
const detailClass = (task) => {
  if (task.detailKind === 'PROBLEM') return 'detail problem';
  return task.detailKind === 'YOURS' && task.attention !== 'OPTIONAL' ? 'detail you' : 'detail';
};

// `blocked` holds the ids of the verbs a press in flight refuses; `offers` the reports offered on this task.
export function Card({task, manyProjects, flagged, blocked, offers}) {
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
      ${task.ask ? html`<span class=${`badge ${task.attention.toLowerCase()}`} data-tip=${task.hint}
        tabIndex=${0}>${task.ask}</span>` : ''}
    </div>
    <div class="heading"><${Flag} taskId=${task.id} on=${flagged}/><div class="title">${task.title || ''}</div></div>
    <${Meta} task=${task} manyProjects=${manyProjects}/>
    ${task.detail ? html`<div class=${detailClass(task)}>${task.detail}</div>` : ''}
    ${rows(task, offers).map(({group, items}) => html`<div class=${`actions ${group}`} data-group=${group}>${
      items.map(({action, offer}) => (action
        ? html`<${Button} task=${task} action=${action} blocked=${blocked}/>`
        : html`<${Offer} task=${task} offer=${offer}/>`))}</div>`)}
  </article>`;
}
