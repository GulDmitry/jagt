// The one task open in full: its card, then a tab of what is known about it.

import {Card} from './Card.js';
import {reportParts} from '../core/format.js';
import {html} from './html.js';
import {Report} from './Report.js';
import {draw} from '../ui/mermaid.js';
import {dayAndMinutes, minutes, sameDay} from '../core/time.js';

export const TABS = [{id: 'overview', label: 'Overview'}, {id: 'round', label: 'Review round'},
  {id: 'artifacts', label: 'Artifacts'}, {id: 'session', label: 'Session'}];

// Today's steps by the clock alone; an older one by its day too.
const when = (at, now) => (sameDay(at, now) ? minutes(at) : dayAndMinutes(at));

const History = ({task, now}) => html`<section class="history" role="tabpanel" id="panel-overview" aria-labelledby="tab-overview">
  <h2 class="group-head">status history</h2>
  <ol>${(task.history || []).map((step) => html`<li>
    <span class="age">${when(step.at, now)}</span> ${step.label || step.status}${
      step.origin ? html` <span class="muted">${step.origin.toLowerCase().replace('_', '-')}</span>` : ''}
  </li>`)}</ol>
</section>`;

// `shown` is what the tab read, `{held, shown}`, or null while it reads.
const Session = ({task, shown}) => html`<section class="session" role="tabpanel" id="panel-session" aria-labelledby="tab-session">
  <pre class="screen">${shown === null ? 'reading…' : shown.held ? shown.shown : 'no window holds this session'}</pre>
  <form class="say" data-say=${task.id}>
    <input name="line" aria-label="tell this session" placeholder="tell this session — no, answer 2 differently"
      autocomplete="off" required/>
    <button type="submit">Send</button>
  </form>
</section>`;

const Round = ({shown, offered}) => html`<section class="round" role="tabpanel" id="panel-round" aria-labelledby="tab-round">
  ${!offered ? html`<p class="muted">Nothing is drafted for this round.</p>`
    : shown === null ? html`<pre class="screen">reading…</pre>`
      : html`<pre class="screen"><${Report} parts=${reportParts(shown)}/></pre>`}
</section>`;

// `shown` is what the tab read, `{names, name, html}`, or null while it reads; `html` is rendered by the server.
const Artifacts = ({task, shown}) => html`<section class="artifacts" role="tabpanel" id="panel-artifacts" aria-labelledby="tab-artifacts">
  ${shown === null ? html`<p class="muted">reading…</p>`
    : !shown.names.length ? html`<p class="muted">No document of this task is in its worktree.</p>`
      : html`<div class="doc-names">${shown.names.map((name) => html`<button data-file=${name} data-task=${task.id}
        aria-pressed=${String(name === shown.name)}>${name}</button>`)}</div>
      <div class="doc" ref=${(root) => draw(root)} dangerouslySetInnerHTML=${{__html: shown.html}}></div>`}
</section>`;

export const Pane = ({card, tab, shown}) => html`<section id="pane" aria-label="the open task">
  <button class="back" data-back="">‹ all tasks</button>
  <${Card} key=${card.task.id} ...${card}/>
  <div role="tablist" class="tabs">${TABS.map((one) => html`<button role="tab" data-tab=${one.id} id=${`tab-${one.id}`}
    aria-selected=${String(one.id === tab)} aria-controls=${one.id === tab ? `panel-${one.id}` : undefined}>${one.label}</button>`)}</div>
  ${tab === 'session' ? html`<${Session} task=${card.task} shown=${shown}/>`
    : tab === 'artifacts' ? html`<${Artifacts} task=${card.task} shown=${shown}/>`
    : tab === 'round' ? html`<${Round} shown=${shown}
      offered=${card.offers.some((offer) => offer.part === 'round')}/>`
    : html`<${History} task=${card.task} now=${card.now}/>`}
</section>`;
