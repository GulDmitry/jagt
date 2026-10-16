// The one task open in full: its card, then a tab of what is known about it.

import {Card} from './Card.js';
import {html} from './html.js';

export const TABS = [{id: 'overview', label: 'Overview'}, {id: 'session', label: 'Session'},
  {id: 'round', label: 'Review round'}];

const History = ({task}) => html`<section class="history" aria-label="status history">
  <h2 class="group-head">status history</h2>
  <ol>${(task.history || []).map((step) => html`<li>
    <span class="age">${new Date(step.at).toLocaleString()}</span> ${step.status}${
      step.origin ? html` <span class="muted">${step.origin.toLowerCase().replace('_', '-')}</span>` : ''}
  </li>`)}</ol>
</section>`;

// `shown` is what the tab read, or null while it reads.
const Session = ({task, shown}) => html`<section class="session" aria-label="the session">
  <p class="muted">The session's screen as it reads now, read-only; Focus opens the terminal itself.</p>
  <pre class="screen">${shown === null ? 'reading…' : shown || 'no window holds this session'}</pre>
  <form class="say" data-say=${task.id}>
    <input name="line" aria-label="tell this session" placeholder="tell this session — no, answer 2 differently"
      autocomplete="off" required/>
    <button type="submit">Send</button>
  </form>
</section>`;

const Round = ({shown, offered}) => html`<section class="round" aria-label="the review round">
  ${!offered ? html`<p class="muted">Nothing is drafted for this round.</p>`
    : html`<pre class="screen">${shown === null ? 'reading…' : shown}</pre>`}
</section>`;

export const Pane = ({card, tab, shown}) => html`<section id="pane" aria-label="the open task">
  <button class="back" data-back="">‹ all tasks</button>
  <${Card} key=${card.task.id} ...${card}/>
  <div role="tablist" class="tabs">${TABS.map((one) => html`<button role="tab" data-tab=${one.id}
    aria-selected=${String(one.id === tab)}>${one.label}</button>`)}</div>
  ${tab === 'session' ? html`<${Session} task=${card.task} shown=${shown}/>`
    : tab === 'round' ? html`<${Round} shown=${shown} offered=${card.offers.some((offer) => offer.id === 'replies')}/>`
    : html`<${History} task=${card.task}/>`}
</section>`;
