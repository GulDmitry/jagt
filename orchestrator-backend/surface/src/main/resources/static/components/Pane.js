// The one task open in full: its card, then a tab of what is known about it.

import {Card} from './Card.js';
import {html} from './html.js';

export const TABS = [{id: 'overview', label: 'Overview'}, {id: 'session', label: 'Session'},
  {id: 'round', label: 'Review round'}];

// Today's steps by the clock alone; an older one by its day too.
const when = (at, now) => (new Date(at).toDateString() === new Date(now).toDateString()
  ? new Date(at).toLocaleTimeString([], {hour: '2-digit', minute: '2-digit'})
  : new Date(at).toLocaleString([], {day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit'}));

const History = ({task, now}) => html`<section class="history" aria-label="status history">
  <h2 class="group-head">status history</h2>
  <ol>${(task.history || []).map((step) => html`<li>
    <span class="age">${when(step.at, now)}</span> ${step.label || step.status}${
      step.origin ? html` <span class="muted">${step.origin.toLowerCase().replace('_', '-')}</span>` : ''}
  </li>`)}</ol>
</section>`;

// `shown` is what the tab read, `{held, shown}`, or null while it reads.
const Session = ({task, shown}) => html`<section class="session" aria-label="the session">
  <p class="muted">The session's screen as it reads now, read-only; Focus opens the terminal itself.</p>
  <pre class="screen">${shown === null ? 'reading…' : shown.held ? shown.shown : 'no window holds this session'}</pre>
  <form class="say" data-say=${task.id}>
    <input name="line" aria-label="tell this session" placeholder="tell this session — no, answer 2 differently"
      autocomplete="off" required/>
    <button type="submit">Send</button>
  </form>
</section>`;

// `format` turns a round's text into its blocks, verdicts and quotes, as the replies report draws them.
const Round = ({shown, offered, format}) => html`<section class="round" aria-label="the review round">
  ${!offered ? html`<p class="muted">Nothing is drafted for this round.</p>`
    : shown === null ? html`<pre class="screen">reading…</pre>`
      : html`<pre class="screen" ref=${(node) => node && node.replaceChildren(...format(shown))}></pre>`}
</section>`;

export const Pane = ({card, tab, shown, format}) => html`<section id="pane" aria-label="the open task">
  <button class="back" data-back="">‹ all tasks</button>
  <${Card} key=${card.task.id} ...${card}/>
  <div role="tablist" class="tabs">${TABS.map((one) => html`<button role="tab" data-tab=${one.id}
    aria-selected=${String(one.id === tab)}>${one.label}</button>`)}</div>
  ${tab === 'session' ? html`<${Session} task=${card.task} shown=${shown}/>`
    : tab === 'round' ? html`<${Round} shown=${shown} format=${format}
      offered=${card.offers.some((offer) => offer.id === 'replies')}/>`
    : html`<${History} task=${card.task} now=${card.now}/>`}
</section>`;
