// The wiring, and nothing else: no module below here has to know that another one exists.

import * as store from './core/store.js';
import {run} from './ui/act.js';
import {openReport, repaintReport, showReport} from './ui/dialogs.js';
import * as filters from './ui/filters.js';
import * as header from './ui/header.js';
import './ui/keys.js';
import * as legend from './ui/legend.js';
import * as launch from './ui/launch.js';
import * as palette from './ui/palette.js';
import {refresh, refreshVerbs} from './ui/refresh.js';
import {onClick, render} from './ui/render.js';
import {sessionLog, showLog} from './ui/toast.js';

const live = document.getElementById('live');
// Cut off from the backend, the board is a picture of the past: nothing on it may act.
const STALE = ['board', 'phases', 'launch', 'palette', 'open-palette', 'reports', 'report-line'];
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
filters.onChange(render);
// Where on this page each part a verb names is.
const parts = {launch: {focus: launch.focusRef}, legend: {section: legend.node}};
palette.wire({partFor: (part) => parts[part] || {}});
showLog(() => showReport('log — this session', sessionLog()));

// A linked task lands in the FILTER rather than a selection of its own: the control that did it is visible, and
// clearing it is a button already on the page.
const deepLink = new URLSearchParams(window.location.search).get('task');
if (deepLink) {
  filters.box.value = deepLink;
}

// A card painted before the verbs arrived hints its offers by their bare id.
async function loadVerbs() {
  await refreshVerbs();
  palette.refreshSuggestions();
  render();
}

let retryIn = 1000;

function listen() {
  const events = new EventSource('/api/events');
  // Every connect reads the board, the first one included: whatever changed while disconnected sent no event.
  events.addEventListener('open', (event) => {
    // The server's first message is also named `open`, and one connect is one read.
    if (event instanceof MessageEvent) return;
    retryIn = 1000;
    connected(true);
    loadVerbs();
    refresh();
  });
  // An open report is read again on the same signal: the round it shows may be the thing that changed.
  events.addEventListener('changed', () => { refresh(); repaintReport(); });
  // A refused connect is final for an EventSource; only a new one tries again.
  events.onerror = () => {
    connected(false);
    if (events.readyState !== EventSource.CLOSED) return;
    setTimeout(listen, retryIn);
    retryIn = Math.min(retryIn * 2, 30000);
  };
}
listen();
// The slow repaint is for the relative clocks ("4m ago") only, which no event can announce.
setInterval(render, 15000);
