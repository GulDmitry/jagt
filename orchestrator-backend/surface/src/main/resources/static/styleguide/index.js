// Each component in each of its states, from the same modules the board renders.

import {render} from 'preact';
import {Card} from '../components/Card.js';
import {html} from '../components/html.js';
import {grouped, TaskList} from '../components/TaskList.js';
import {JobsTable} from '../components/JobsTable.js';
import {Pane} from '../components/Pane.js';
import {ProjectChecks} from '../components/ProjectChecks.js';
import '../ui/tips.js';
import {cards} from './cards.js';

const State = ({name, children}) => html`<figure class="state" data-state=${name}>
  <figcaption class="muted">${name}</figcaption>${children}</figure>`;

const DRAFTED = ['1 · FIXED · Renamed the helper.', '  > why is this public?',
  '2 · QUESTION · Is the cap per project? https://example.com/ABC-42.', '3 · PUSHED BACK · The ring forbids it.'].join('\n');

const keys = 'j k walk · ↵ open · n next yours · f focus · t new task · / filter · ⌘K command';
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
      <${TaskList} groups=${grouped(tasks, false)} selected="ABC-70" changed=${changed} now=${Date.now()}/></${State}>
    <${State} name="the keys line under the list"><${TaskList} groups=${grouped(tasks.slice(0, 2), false)} selected="ABC-42"
      changed=${() => false} now=${Date.now()} keys=${keys}/></${State}>
    <${State} name="several projects"><${TaskList} groups=${grouped(tasks, true)} selected="ABC-42"
      changed=${() => false} now=${Date.now()}/></${State}>
    <${State} name="the open task, overview"><${Pane} card=${cards[0].props} tab="overview" shown=${null}/></${State}>
    <${State} name="the open task, its session"><${Pane} card=${cards[1].props} tab="session"
      shown=${{held: true, shown: '› npm test\n  ✗ restores the stored mode on load'}}/></${State}>
    <${State} name="the open task, its session has no window"><${Pane} card=${cards[1].props} tab="session"
      shown=${{held: false, shown: ''}}/></${State}>
    <${State} name="the open task, its session shows a blank screen"><${Pane} card=${cards[1].props} tab="session"
      shown=${{held: true, shown: ''}}/></${State}>
    <${State} name="a round drafted: two blocks, a quote, the three verdicts"><${Pane} card=${{...cards[1].props,
      offers: [{id: 'replies', hint: 'the drafted answers', part: 'round'}]}} tab="round" shown=${DRAFTED}/></${State}>
    <${State} name="the open task, a round not drafted"><${Pane} card=${cards[1].props} tab="round" shown=${null}/></${State}>
  </main>
</section>
<section aria-label="Jobs">
  <h2>Jobs</h2>
  <main><${State} name="one failed, one never run, one running, one whose last run passed"><${JobsTable} now=${Date.now()} jobs=${[
    {id: 'verify', describe: 'runs the project’s verify command', serves: 'your tasks', lastStartedAt: Date.now() - 360000,
      lastError: 'exit 1: 2 tests fail', nextRunAt: Date.now() + 14000, running: false},
    {id: 'intake', describe: 'starts a task on every item marked ready', serves: 'the tracker', lastStartedAt: null,
      lastError: null, nextRunAt: Date.now() + 540000, running: false},
    {id: 'usage', describe: 'reads the subscription’s limits', serves: 'housekeeping', lastStartedAt: Date.now(),
      lastError: null, nextRunAt: null, running: true},
    {id: 'cleanup', describe: 'removes the worktrees of closed tasks', serves: 'housekeeping', lastStartedAt: Date.now() - 600000,
      lastError: null, nextRunAt: Date.now() + 3000000, running: false}]}/></${State}></main>
</section>
<section aria-label="Project checks">
  <h2>Project checks</h2>
  <main><${State} name="a repository ready to add"><ul class="checks-list"><${ProjectChecks} lines=${[
    {ok: true, text: 'a git repository, origin git@host:g/billing.git'}, {ok: true, text: 'tasks are cut from origin/main'},
    {ok: false, text: 'the key billing is taken: pick another'}]}/></ul></${State}></main>
</section>`, document.getElementById('styleguide'));
