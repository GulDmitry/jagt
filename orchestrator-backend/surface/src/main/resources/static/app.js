// The wiring, and nothing else: no module below here has to know that another one exists.

import * as store from './core/store.js';
import {run} from './ui/act.js';
import {openReport, showReport} from './ui/dialogs.js';
import * as filters from './ui/filters.js';
import * as header from './ui/header.js';
import './ui/keys.js';
import * as legend from './ui/legend.js';
import * as launch from './ui/launch.js';
import * as palette from './ui/palette.js';
import {refresh, refreshVerbs} from './ui/refresh.js';
import {onClick, render} from './ui/render.js';
import * as resume from './ui/resume.js';
import {sessionLog, showLog} from './ui/toast.js';

const live = document.getElementById('live');

onClick({
  action: run,
  report: (id, about) => openReport(`${id} ${store.nameOf(about)}`,
    `/api/commands/${id}?about=${encodeURIComponent(about)}`),
});
header.onNarrow(render);
filters.onChange(render);
palette.wire({
  focusRef: launch.focusRef,
  openResume: resume.open,
  // Half of "how does this work" on a board is what its marks mean.
  reportSection: (id) => (id === 'help' ? legend.node() : null),
});
showLog(() => showReport('log — this session', sessionLog()));

// A linked task lands in the FILTER rather than a selection of its own: the control that did it is visible, and
// clearing it is a button already on the page.
const deepLink = new URLSearchParams(window.location.search).get('task');
if (deepLink) {
  filters.box.value = deepLink;
}

async function loadVerbs() {
  await refreshVerbs();
  palette.refreshSuggestions();
  // The hint is the server's, whether resume is reached as a form or as a verb.
  resume.describe(palette.hintFor('resume'));
}

const events = new EventSource('/api/events');
events.addEventListener('open', () => {
  live.classList.add('on');
  loadVerbs();
  refresh();
});
events.addEventListener('changed', refresh);
events.onerror = () => live.classList.remove('on');
// The slow repaint is for the relative clocks ("4m ago") only, which no event can announce.
setInterval(render, 15000);
loadVerbs();
refresh();
