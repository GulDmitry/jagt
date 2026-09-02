// Tier 2, behind ⌘K because it costs a model call: free text mapped to ONE command, run by the gate a button uses.

import {api, refusal} from '../core/api.js';
import * as store from '../core/store.js';
import {run} from './act.js';
import {openReport} from './dialogs.js';
import {refresh} from './refresh.js';
import {sending} from './submit.js';
import {toast} from './toast.js';

const form = document.getElementById('palette');
const ask = document.getElementById('ask');
const opener = document.getElementById('open-palette');
const verdict = document.getElementById('palette-state');

// Asked here, decided at wiring time, so this module knows no form and no verb by name.
let forms = {focusRef: () => {}, openResume: () => {}, reportSection: () => null};

export const wire = (wired) => { forms = wired; };

// One place for the hints, so no control keeps a copy of the words.
export const hintFor = (id) => (verbFor(id) || {}).hint || '';

export const isOpen = () => !form.hidden;

function show(open) {
  form.hidden = !open;
  opener.setAttribute('aria-pressed', String(open));
  if (open) {
    ask.focus();
    ask.select();
  }
}

export const close = () => show(false);

export function toggle() {
  show(form.hidden);
  judge();
}

// A retired spelling is accepted and never offered: two spellings on screen are two answers to one question.
function verbFor(word) {
  const typed = word.toLowerCase();
  // Its own name first, exactly as the server resolves it: an alias must never shadow another verb's id.
  return store.verbs().find((verb) => verb.id === typed)
    || store.verbs().find((verb) => (verb.aliases || []).includes(typed));
}

// Understood WITHOUT a model: a known verb, and — for the per-task ones — a task that exists.
function parse(line) {
  const tokens = line.trim().split(/\s+/).filter(Boolean);
  if (!tokens.length) return null;
  const verb = verbFor(tokens[0]);
  if (!verb) return null;
  const argument = tokens.slice(1).join(' ');
  if (!verb.takesTask) return {verb, argument};
  return {verb, argument, task: store.taskFor(argument)};
}

// A report about ONE task gets no button: the card that has something to show is where it is pressed, and a bar
// button would answer for all of them.
export function refreshSuggestions() {
  document.getElementById('ask-options').replaceChildren(
    ...store.verbs().map((verb) => Object.assign(document.createElement('option'),
      {value: verb.id, label: verb.hint})));
  document.getElementById('reports').replaceChildren(
    ...store.verbs().filter((verb) => verb.report && !verb.aboutOneTask).map((verb) => {
      const button = document.createElement('button');
      button.id = `show-${verb.id}`;
      button.textContent = verb.id.charAt(0).toUpperCase() + verb.id.slice(1);
      button.dataset.tip = verb.hint;
      button.onclick = () => openReport(`${verb.id} — ${verb.hint}`, `/api/commands/${verb.id}`,
        {extra: forms.reportSection(verb.id)});
      return button;
    }));
}

// The verdict, live: a typo must be visible before Run, not after a model has been paid to guess at it.
export function judge() {
  const line = ask.value.trim();
  verdict.classList.remove('ok', 'bad');
  if (!line) {
    verdict.textContent = '';
    return;
  }
  const parsed = parse(line);
  if (!parsed) {
    const word = line.split(/\s+/)[0];
    verdict.textContent = verbFor(word)
      ? ''
      : `“${word}” is not a command — this will go to the model as plain words`;
    return;
  }
  if (parsed.verb.takesTask && !parsed.task) {
    verdict.classList.add('bad');
    verdict.textContent = parsed.argument
      ? `no task “${parsed.argument}” — use a ticket id or its alias`
      : `${parsed.verb.id} needs a task: ${parsed.verb.id} <ticket|alias>`;
    return;
  }
  verdict.classList.add('ok');
  verdict.textContent = `runs as typed — ${parsed.verb.hint}`;
}

// A line the backend answered without creating anything is the line that would repeat the attempt.
const HANDLED = {clear: true};
const KEPT = {clear: false};

// A line that parses is EXECUTED, not interpreted: only real free text is worth a model call.
async function runParsed(parsed) {
  const {verb, argument, task} = parsed;
  if (verb.takesTask) {
    await run(task.id, verb.id);
    return HANDLED;
  }
  // What was typed after the verb goes with it: a report that narrows to one task must not answer for all.
  if (verb.report) {
    const narrowed = argument ? `?about=${encodeURIComponent(argument)}` : '';
    const opened = await openReport(`${verb.id} ${store.nameOf(argument)}`.trim(),
      `/api/commands/${encodeURIComponent(verb.id)}${narrowed}`,
      {about: verb.aboutOneTask ? argument : null});
    // A report that could not be read leaves the typed line where it was, to try again.
    return opened ? HANDLED : KEPT;
  }
  // With no argument the palette hands over to the form, which is where the rest of a launch is decided anyway.
  if (verb.id === 'do') {
    if (!argument) { forms.focusRef(); return HANDLED; }
    const result = await api('/api/tasks/line', {
      method: 'POST',
      headers: {'Content-Type': 'application/json'},
      body: JSON.stringify({line: argument}),
    });
    toast(result.message);
    return result.created ? HANDLED : KEPT;
  }
  if (verb.id === 'resume') {
    if (!argument.startsWith('http')) {
      forms.openResume(argument);
      return HANDLED;
    }
    const result = await api('/api/tasks/resume', {
      method: 'POST',
      headers: {'Content-Type': 'application/json'},
      body: JSON.stringify({reviewRequestUrl: argument}),
    });
    toast(result.message);
    return result.created ? HANDLED : KEPT;
  }
  return null;
}

ask.addEventListener('input', judge);
opener.onclick = toggle;

form.onsubmit = async (event) => {
  event.preventDefault();
  const parsed = parse(ask.value);
  // A verb typed ALONE is answered here; one whose argument named no task is prose and stays tier 2's job.
  if (parsed && parsed.verb.takesTask && !parsed.task && !parsed.argument) {
    judge();
    return;
  }
  if (parsed && (!parsed.verb.takesTask || parsed.task)) {
    // Tier 1 answers for itself, so there is no one message to show and nothing to say while it happens.
    const button = form.querySelector('button[type=submit]');
    button.disabled = true;
    try {
      const answered = await runParsed(parsed);
      if (answered) {
        if (answered.clear) {
          ask.value = '';
          close();
        }
        return;
      }
    } catch (e) {
      toast(refusal(e), true);      // a line that reached the backend and was refused is not tier 2's to retry
      return;
    } finally {
      button.disabled = false;
      judge();
      await refresh();
    }
  }
  await sending(form, {
    waiting: 'interpreting…',                    // a model call: seconds
    send: () => api('/api/interpret', {
      method: 'POST',
      headers: {'Content-Type': 'application/json'},
      body: JSON.stringify({text: ask.value}),
    }),
    done: () => {
      ask.value = '';
      close();
    },
  });
};
