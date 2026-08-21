// Plain DOM on purpose: no build step, no CDN, works with the machine offline.
//
// It holds NO rules of its own: every card renders the server's projection (phase, owner, hint, legal
// actions), and a button posts the action id back. If the server did not list an action, there is no button —
// and if the page is stale, the POST is refused with a sentence, which is what the toast shows.
//
// Two rings: `core/` answers a question without owning a node on the page, `ui/` owns the nodes it renders.
// THIS file only WIRES them, which is why no module has to know that another one exists.

import {api, refusal, text} from './core/api.js';
import * as store from './core/store.js';
import {run} from './ui/act.js';
import {openReport, showReport} from './ui/dialogs.js';
import * as filters from './ui/filters.js';
import * as header from './ui/header.js';
import * as projects from './ui/projects.js';
import {refresh, refreshVerbs} from './ui/refresh.js';
import {onClick, render} from './ui/render.js';
import {sessionLog, toast} from './ui/toast.js';

const live = document.getElementById('live');

onClick({
  action: run,
  report: (id, about) => openReport(`${id} ${about}`, `/api/commands/${id}?about=${encodeURIComponent(about)}`),
});
header.onNarrow(render);
filters.onChange(render);
document.getElementById('show-log').onclick = () => showReport('log — this session', sessionLog());

const launchForm = document.getElementById('launch');

function toggleForm(form, button, onOpen) {
  form.hidden = !form.hidden;
  button.setAttribute('aria-pressed', String(!form.hidden));
  if (!form.hidden && onOpen) onOpen();
}

launchForm.onsubmit = async (event) => {
  event.preventDefault();
  const state = document.getElementById('launch-state');
  const button = launchForm.querySelector('button[type=submit]');
  button.disabled = true;
  state.textContent = 'reading the ticket…';        // a model call: seconds, sometimes tens of them
  try {
    const result = await api('/api/tasks', {
      method: 'POST',
      headers: {'Content-Type': 'application/json'},
      body: JSON.stringify({
        ref: document.getElementById('ref').value,
        project: projects.chosen(),
        mode: document.getElementById('plan').checked ? 'plan' : null,
        baseBranch: document.getElementById('base-branch').value,
        notes: document.getElementById('notes').value,
      }),
    });
    toast(result.message);
    launchForm.reset();
    projects.forget();
  } catch (e) {
    toast(e.message, true);
  } finally {
    button.disabled = false;
    state.textContent = '';
    await refresh();
  }
};

const resumeForm = document.getElementById('resume');
document.getElementById('resume-task').onclick = () =>
  toggleForm(resumeForm, document.getElementById('resume-task'),
    () => document.getElementById('resume-url').focus());
resumeForm.onsubmit = async (event) => {
  event.preventDefault();
  const state = document.getElementById('resume-state');
  const button = resumeForm.querySelector('button[type=submit]');
  button.disabled = true;
  state.textContent = 'reading the review request…';        // a model call unless a CodeHost is configured
  try {
    const result = await api('/api/tasks/resume', {
      method: 'POST',
      headers: {'Content-Type': 'application/json'},
      body: JSON.stringify({reviewRequestUrl: document.getElementById('resume-url').value}),
    });
    toast(result.message);
    resumeForm.reset();
    resumeForm.hidden = true;
  } catch (e) {
    toast(e.message, true);
  } finally {
    button.disabled = false;
    state.textContent = '';
    await refresh();
  }
};

// A desktop notification about one task links here with `?task=<id>`, and it lands in the FILTER rather than in
// a selection of its own: the card then stands alone with its actions, the control that did it is visible, and
// clearing it is the button already on the page. An id nothing matches shows the "no task matches" line, which
// is the truth — the task was closed while the banner sat there.
const deepLink = new URLSearchParams(window.location.search).get('task');
if (deepLink) {
  filters.box.value = deepLink;
}

// Tier 2 of the dispatch: free text, mapped to ONE command by a model and executed by the same gate the
// buttons use. Kept behind ⌘K rather than in the way, because tier 1 (a button, a typed command) costs
// nothing and this costs a model call — the point is flexibility when it is wanted, not by default.
const palette = document.getElementById('palette');
const ask = document.getElementById('ask');

function togglePalette(show) {
  palette.hidden = !show;
  document.getElementById('open-palette').setAttribute('aria-pressed', String(show));
  if (show) {
    ask.focus();
    ask.select();
  }
}

document.getElementById('open-palette').onclick = () => { togglePalette(palette.hidden); judgeAsk(); };
const typingInto = (target) => target instanceof HTMLInputElement || target instanceof HTMLTextAreaElement
  || target instanceof HTMLSelectElement;

document.addEventListener('keydown', (event) => {
  if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 'k') {
    event.preventDefault();
    togglePalette(palette.hidden);
  } else if (event.key === 'Escape' && !palette.hidden) {
    togglePalette(false);
  } else if (event.key === '/' && !typingInto(event.target)) {
    event.preventDefault();
    filters.box.focus();
  } else if (event.key === 'Escape' && event.target === filters.box) {
    filters.box.value = '';
    render();
  }
});

// A verb answers to its own name and to whatever it was renamed from: accepted here as the console accepts it,
// and offered in neither.
function verbFor(word) {
  const typed = word.toLowerCase();
  // Its own name first, exactly as the server resolves it: an alias must never shadow another verb's id.
  return store.verbs().find((v) => v.id === typed)
    || store.verbs().find((v) => (v.aliases || []).includes(typed));
}

// Understood WITHOUT a model: a known verb, and — for the per-task ones — a task that actually exists. Anything
// else is left to tier 2.
function parseCommand(line) {
  const tokens = line.trim().split(/\s+/).filter(Boolean);
  if (!tokens.length) return null;
  const verb = verbFor(tokens[0]);
  if (!verb) return null;
  const argument = tokens.slice(1).join(' ');
  if (!verb.takesTask) return {verb, argument};
  return {verb, argument, task: store.taskFor(argument)};
}

function refreshSuggestions() {
  document.getElementById('ask-options').replaceChildren(
    ...store.verbs().map((verb) => Object.assign(document.createElement('option'),
      {value: verb.id, label: verb.hint})));
  // One button per report the SERVER declares, so a new one appears in the toolbar without this page learning
  // its name.
  document.getElementById('reports').replaceChildren(
    ...store.verbs().filter((verb) => verb.report).map((verb) => {
      const button = document.createElement('button');
      button.id = `show-${verb.id}`;
      button.textContent = verb.id.charAt(0).toUpperCase() + verb.id.slice(1);
      button.dataset.tip = verb.hint;
      button.onclick = () => openReport(`${verb.id} — ${verb.hint}`, `/api/commands/${verb.id}`);
      return button;
    }));
  const resume = verbFor('resume');
  if (resume) {
    document.querySelectorAll('#resume-task, #resume button[type=submit]')
      .forEach((button) => { button.dataset.tip = resume.hint; });
  }
}

// The verdict, live: a typo must be visible before Run, not after a model has been paid to guess at it.
function judgeAsk() {
  const state = document.getElementById('palette-state');
  const line = ask.value.trim();
  state.classList.remove('ok', 'bad');
  if (!line) {
    state.textContent = '';
    return;
  }
  const parsed = parseCommand(line);
  if (!parsed) {
    const word = line.split(/\s+/)[0];
    const known = Boolean(verbFor(word));
    state.textContent = known ? '' : `“${word}” is not a command — this will go to the model as plain words`;
    return;
  }
  if (parsed.verb.takesTask && !parsed.task) {
    state.classList.add('bad');
    state.textContent = parsed.argument
      ? `no task “${parsed.argument}” — use a ticket id or its alias`
      : `${parsed.verb.id} needs a task: ${parsed.verb.id} <ticket|alias>`;
    return;
  }
  state.classList.add('ok');
  state.textContent = `runs as typed — ${parsed.verb.hint}`;
}

ask.addEventListener('input', judgeAsk);

// Tier 1 first: a line that parses is EXECUTED, not interpreted — deterministic, instant and free. Only real
// free text reaches /api/interpret.
async function runParsed(parsed) {
  const {verb, argument, task} = parsed;
  if (verb.takesTask) {
    await run(task.id, verb.id);
    return `${verb.id} ${task.alias || task.id}`;
  }
  // Reports are named by the server rather than listed here, so one more needs no branch in this page. What was
  // typed after the verb goes with it: a report that narrows to one task must not silently answer for all of them.
  if (verb.report) {
    const about = argument ? `?about=${encodeURIComponent(argument)}` : '';
    showReport(`${verb.id} ${argument}`.trim(),
      await text(`/api/commands/${encodeURIComponent(verb.id)}${about}`));
    return `${verb.id} ${argument}`.trim();
  }
  if (verb.id === 'do') {
    if (!argument) { document.getElementById('ref').focus(); return 'do'; }
    const result = await api('/api/tasks', {
      method: 'POST',
      headers: {'Content-Type': 'application/json'},
      body: JSON.stringify({ref: argument}),
    });
    toast(result.message);
    return `do ${argument}`;
  }
  if (verb.id === 'resume') {
    if (!argument.startsWith('http')) {
      document.getElementById('resume-task').click();
      document.getElementById('resume-url').value = argument;
      return 'resume';
    }
    const result = await api('/api/tasks/resume', {
      method: 'POST',
      headers: {'Content-Type': 'application/json'},
      body: JSON.stringify({reviewRequestUrl: argument}),
    });
    toast(result.message);
    return `resume ${argument}`;
  }
  return null;
}

palette.onsubmit = async (event) => {
  event.preventDefault();
  const state = document.getElementById('palette-state');
  const button = palette.querySelector('button[type=submit]');
  const parsed = parseCommand(ask.value);
  // A verb typed ALONE is answered here: the model would be paid to map a line with no argument at all, and
  // would answer "unknown command" for a verb the page just completed. A verb WITH an argument that named no
  // task is prose ("ship the widget one") and stays tier 2's job.
  if (parsed && parsed.verb.takesTask && !parsed.task && !parsed.argument) {
    judgeAsk();
    return;
  }
  if (parsed && (!parsed.verb.takesTask || parsed.task)) {
    button.disabled = true;
    try {
      const ran = await runParsed(parsed);
      if (ran) { ask.value = ''; togglePalette(false); return; }
    } catch (e) {
      toast(refusal(e), true);
      return;
    } finally {
      button.disabled = false;
      judgeAsk();
      await refresh();
    }
  }
  button.disabled = true;
  state.textContent = 'interpreting…';           // a model call: seconds
  try {
    const result = await api('/api/interpret', {
      method: 'POST',
      headers: {'Content-Type': 'application/json'},
      body: JSON.stringify({text: ask.value}),
    });
    toast(result.message);
    ask.value = '';
    togglePalette(false);
  } catch (e) {
    toast(refusal(e), true);
  } finally {
    button.disabled = false;
    state.textContent = '';
    await refresh();
  }
};

// A <dialog> closes on Escape by itself; the inline forms do not.
document.addEventListener('keydown', (event) => {
  if (event.key !== 'Escape') return;
  for (const [id, button] of [['resume', 'resume-task'], ['palette', 'open-palette']]) {
    const form = document.getElementById(id);
    if (form && !form.hidden) {
      form.hidden = true;
      document.getElementById(button).setAttribute('aria-pressed', 'false');
    }
  }
});

async function loadVerbs() {
  await refreshVerbs();
  refreshSuggestions();
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
