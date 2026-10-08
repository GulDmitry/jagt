// Tier 2, behind ⌘K because it costs a model call: free text mapped to ONE command, run by the gate a button uses.

import {api, refusal} from '../core/api.js';
import {parse, verdictOf} from '../core/grammar.js';
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
let forms = {partFor: () => ({})};

export const wire = (wired) => { forms = wired; };

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
        {extra: forms.partFor(verb.part).section?.()});
      return button;
    }));
}

export function judge() {
  const {kind, text} = verdictOf(ask.value.trim());
  verdict.classList.remove('ok', 'bad');
  if (kind) verdict.classList.add(kind);
  verdict.textContent = text;
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
  const part = forms.partFor(verb.part);
  // What was typed after the verb goes with it: a report that narrows to one task must not answer for all.
  if (verb.report) {
    const narrowed = argument ? `?about=${encodeURIComponent(argument)}` : '';
    const opened = await openReport(`${verb.id} ${store.nameOf(argument)}`.trim(),
      `/api/commands/${encodeURIComponent(verb.id)}${narrowed}`,
      {about: verb.aboutOneTask ? argument : null, extra: part.section?.()});
    // A report that could not be read leaves the typed line where it was, to try again.
    return opened ? HANDLED : KEPT;
  }
  // Typed alone, a verb with a part of the board hands over to it, where the rest of its inputs are chosen.
  if (!argument && part.focus) {
    part.focus();
    return HANDLED;
  }
  const narrowed = argument ? `?about=${encodeURIComponent(argument)}` : '';
  const result = await api(`/api/commands/${encodeURIComponent(verb.id)}${narrowed}`, {method: 'POST'});
  toast(result.message);
  return HANDLED;
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
