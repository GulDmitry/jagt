// The one task open in full: its card, then every status it passed through.

import {Card} from './card.js';
import {html} from './html.js';

const History = ({task}) => html`<section class="history" aria-label="status history">
  <h2 class="group-head">status history</h2>
  <ol>${(task.history || []).map((step) => html`<li>
    <span class="age">${new Date(step.at).toLocaleString()}</span> ${step.status}${
      step.origin ? html` <span class="muted">${step.origin.toLowerCase().replace('_', '-')}</span>` : ''}
  </li>`)}</ol>
</section>`;

export const Pane = ({card}) => html`<section id="pane" aria-label="the open task">
  <button class="back" data-back="">‹ all tasks</button>
  <${Card} key=${card.task.id} ...${card}/>
  <${History} task=${card.task}/>
</section>`;
