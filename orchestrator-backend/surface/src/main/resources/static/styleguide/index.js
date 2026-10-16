// Each component in each of its states, from the same modules the board renders.

import {render} from 'preact';
import {Card} from '../ui/card.js';
import {html} from '../ui/html.js';
import {grouped, TaskList} from '../ui/list.js';
import {Pane} from '../ui/pane.js';
import '../ui/tips.js';
import {cards} from './cards.js';

const State = ({name, children}) => html`<figure class="state" data-state=${name}>
  <figcaption class="muted">${name}</figcaption>${children}</figure>`;

const tasks = cards.map(({props}) => props.task);
const changed = (task) => task.alias === 'a3';

render(html`<section aria-label="Card">
  <h2>Card</h2>
  <main>${cards.map(({name, props}) => html`<${State} name=${name}><${Card} ...${props}/></${State}>`)}</main>
</section>
<section aria-label="Task list">
  <h2>Task list</h2>
  <main>
    <${State} name="one project: yours first, a3 changed since it was opened, a4 selected">
      <${TaskList} groups=${grouped(tasks, false)} selected="ABC-70" changed=${changed}/></${State}>
    <${State} name="several projects"><${TaskList} groups=${grouped(tasks, true)} selected="ABC-42"
      changed=${() => false}/></${State}>
    <${State} name="the open task"><${Pane} card=${cards[0].props}/></${State}>
  </main>
</section>`, document.getElementById('styleguide'));
