// The wiring, and nothing else: no module below here has to know that another one exists.

import * as store from './core/store.js';
import * as addProject from './ui/addproject.js';
import {run, runGlobal} from './ui/act.js';
import {openReport, repaintReport, showReport} from './ui/dialogs.js';
import * as filters from './ui/filters.js';
import * as header from './ui/header.js';
import './ui/interactions.js';
import * as jobs from './ui/jobs.js';
import './ui/keys.js';
import * as legend from './ui/legend.js';
import * as launch from './ui/launch.js';
import './ui/menu.js';
import * as palette from './ui/palette.js';
import {refresh, refreshVerbs} from './ui/refresh.js';
import {onSent} from './ui/submit.js';
import {onClick, render} from './ui/render.js';
import * as selection from './ui/selection.js';
import * as tabs from './ui/tabs.js';
import {sessionLog, showLog} from './ui/toast.js';

const live = document.getElementById('live');
// Cut off from the backend, the board is a picture of the past: nothing on it may act.
const STALE = ['board', 'phases', 'new-task', 'empty-new', 'palette', 'open-palette', 'reports-toggle', 'report-line',
  'act-stopped', 'jobs-pulse', 'launch', 'project-form', 'empty-project'];
const connected = (yes) => {
  live.classList.toggle('on', yes);
  document.getElementById('offline').hidden = yes;
  document.body.classList.toggle('stale', !yes);
  STALE.forEach((id) => { document.getElementById(id).inert = !yes; });
};

onClick({
  action: run,
  report: (id, about) => openReport(`${id} ${store.nameOf(about)}`,
    `/api/commands/${id}?about=${encodeURIComponent(about)}`, {about}),
});
header.onBarClick(render);
onSent(refresh);
header.onVerbClick(runGlobal);
header.onJobsClick(() => openReport('jobs — what runs unattended', '/api/commands/jobs', {extra: jobs.node()}));
document.getElementById('new-task').onclick = launch.open;
document.getElementById('empty-new').onclick = launch.open;
document.getElementById('add-project').onclick = addProject.open;
document.getElementById('empty-project').onclick = addProject.open;
filters.onChange(render);
// Where on this page each part a verb names is.
const parts = {launch: {focus: launch.open}, legend: {section: legend.node}, jobs: {section: jobs.node}};
palette.wire({partFor: (part) => parts[part] || {}});
showLog(() => showReport('log — this session', sessionLog()));

// A linked task lands in the FILTER rather than a selection of its own: the control that did it is visible, and
// clearing it is a button already on the page.
const deepLink = new URLSearchParams(window.location.search).get('task');
if (deepLink) {
  filters.box.value = deepLink;
  selection.link(deepLink);
}

// A card painted before the verbs arrived hints its offers by their bare id.
async function loadVerbs() {
  await refreshVerbs();
  palette.refreshSuggestions();
  render();
}

let retryIn = 1000;
let servedBy = null;

function retry() {
  setTimeout(listen, retryIn);
  retryIn = Math.min(retryIn * 2, 30000);
}

function listen() {
  const events = new EventSource('/api/events');
  let silence = 0;
  // Every connect reads the board, the first one included: whatever changed while disconnected sent no event.
  events.addEventListener('open', (event) => {
    // The server's first message is also named `open`, and one connect is one read. It names the run: a page
    // that outlived a restart is still the old board's code, so it loads the new one.
    if (event instanceof MessageEvent) {
      if (servedBy && servedBy !== event.data) window.location.reload();
      servedBy = event.data;
      return;
    }
    retryIn = 1000;
    connected(true);
    loadVerbs();
    refresh();
  });
  // A connection a sleeping laptop dropped stays open to the page: two missed beats are what says it is gone.
  events.addEventListener('beat', (beat) => {
    clearTimeout(silence);
    silence = setTimeout(() => {
      events.close();
      connected(false);
      retry();
    }, Number(beat.data) * 2.5);
  });
  // An open report is read again on the same signal: the round it shows may be the thing that changed.
  events.addEventListener('changed', () => { tabs.forget(); refresh(); repaintReport(); });
  // A refused connect is final for an EventSource; only a new one tries again.
  events.onerror = () => {
    clearTimeout(silence);
    connected(false);
    if (events.readyState === EventSource.CLOSED) retry();
  };
}
listen();
// The slow repaint is for the relative clocks ("4m ago") only, which no event can announce.
setInterval(render, 15000);
