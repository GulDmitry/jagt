// The board: plain DOM on purpose — no build step, no CDN, works with the machine offline.
//
// It holds NO rules of its own: every card renders the server's projection (phase, owner, hint, legal actions),
// and a button posts the action id back. If the server did not list an action, there is no button — and if the
// page is stale, the POST is refused with a sentence, which is what the toast shows.
//
// Two rings: `core/` answers a question without owning a node on the page, `ui/` owns the nodes it renders.
// THIS file only WIRES them together — which is why no module has to know that another one exists, and why
// nothing below it reaches for a node it did not create.

import {run} from './ui/act.js';
import {openReport, showReport} from './ui/dialogs.js';
import * as filters from './ui/filters.js';
import * as header from './ui/header.js';
import './ui/keys.js';
import * as launch from './ui/launch.js';
import * as palette from './ui/palette.js';
import {refresh, refreshVerbs} from './ui/refresh.js';
import {onClick, render} from './ui/render.js';
import * as resume from './ui/resume.js';
import {sessionLog, showLog} from './ui/toast.js';

const live = document.getElementById('live');

onClick({
  action: run,
  report: (id, about) => openReport(`${id} ${about}`, `/api/commands/${id}?about=${encodeURIComponent(about)}`),
});
header.onNarrow(render);
filters.onChange(render);
palette.wire({focusRef: launch.focusRef, openResume: resume.open});
showLog(() => showReport('log — this session', sessionLog()));

// A desktop notification about one task links here with `?task=<id>`, and it lands in the FILTER rather than in
// a selection of its own: the card then stands alone with its actions, the control that did it is visible, and
// clearing it is the button already on the page. An id nothing matches shows the "no task matches" line, which
// is the truth — the task was closed while the banner sat there.
const deepLink = new URLSearchParams(window.location.search).get('task');
if (deepLink) {
  filters.box.value = deepLink;
}

async function loadVerbs() {
  await refreshVerbs();
  palette.refreshSuggestions();
  // `resume` is a form here and a verb there, and the hint is the server's either way.
  resume.describe(palette.hintFor('resume'));
}

// Push, not poll: the backend tells us when state changed. The slow interval only refreshes the relative
// clocks ("4m ago"), which no event can announce.
// A reconnect is not a resync: the events missed while the backend was down are gone.
const events = new EventSource('/api/events');
events.addEventListener('open', () => {
  live.classList.add('on');
  loadVerbs();
  refresh();
});
events.addEventListener('changed', refresh);
events.onerror = () => live.classList.remove('on');
setInterval(render, 15000);
loadVerbs();
refresh();
