// Tier 2 of the dispatch: free text, mapped to ONE command by a model and executed by the same gate the buttons
// use. Kept behind ⌘K rather than in the way, because tier 1 (a button, a typed command) costs nothing and this
// costs a model call — the point is flexibility when it is wanted, not by default.

import {api, refusal, text} from '../core/api.js';
import * as store from '../core/store.js';
import {run} from './act.js';
import {openReport, showReport} from './dialogs.js';
import {refresh} from './refresh.js';
import {sending} from './submit.js';
import {toast} from './toast.js';

const form = document.getElementById('palette');
const ask = document.getElementById('ask');
const opener = document.getElementById('open-palette');
const verdict = document.getElementById('palette-state');

// What the palette hands over to the forms it cannot reach into: a launch it can only focus, and a resume that
// takes the URL that was typed.
let forms = {focusRef: () => {}, openResume: () => {}};

export const wire = (wired) => { forms = wired; };

// The verb list is fetched here, so a hint another control wants comes from here too rather than from a second
// fetch — or worse, from a copy of the words.
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

// A verb answers to its own name and to whatever it was renamed from: accepted here as the console accepts it,
// and offered in neither.
function verbFor(word) {
  const typed = word.toLowerCase();
  // Its own name first, exactly as the server resolves it: an alias must never shadow another verb's id.
  return store.verbs().find((verb) => verb.id === typed)
    || store.verbs().find((verb) => (verb.aliases || []).includes(typed));
}

// Understood WITHOUT a model: a known verb, and — for the per-task ones — a task that actually exists. Anything
// else is left to tier 2.
function parse(line) {
  const tokens = line.trim().split(/\s+/).filter(Boolean);
  if (!tokens.length) return null;
  const verb = verbFor(tokens[0]);
  if (!verb) return null;
  const argument = tokens.slice(1).join(' ');
  if (!verb.takesTask) return {verb, argument};
  return {verb, argument, task: store.taskFor(argument)};
}

// The suggestions and the report buttons are the SERVER's verb list, so a command the console accepts can never
// be missing here, and one more report needs no branch in this page.
export function refreshSuggestions() {
  document.getElementById('ask-options').replaceChildren(
    ...store.verbs().map((verb) => Object.assign(document.createElement('option'),
      {value: verb.id, label: verb.hint})));
  document.getElementById('reports').replaceChildren(
    ...store.verbs().filter((verb) => verb.report).map((verb) => {
      const button = document.createElement('button');
      button.id = `show-${verb.id}`;
      button.textContent = verb.id.charAt(0).toUpperCase() + verb.id.slice(1);
      button.dataset.tip = verb.hint;
      button.onclick = () => openReport(`${verb.id} — ${verb.hint}`, `/api/commands/${verb.id}`);
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

// Tier 1: a line that parses is EXECUTED, not interpreted — deterministic, instant and free. Only real free text
// reaches /api/interpret.
async function runParsed(parsed) {
  const {verb, argument, task} = parsed;
  if (verb.takesTask) {
    await run(task.id, verb.id);
    return `${verb.id} ${task.alias || task.id}`;
  }
  // What was typed after the verb goes with it: a report that narrows to one task must not silently answer for
  // all of them.
  if (verb.report) {
    const about = argument ? `?about=${encodeURIComponent(argument)}` : '';
    showReport(`${verb.id} ${argument}`.trim(),
      await text(`/api/commands/${encodeURIComponent(verb.id)}${about}`));
    return `${verb.id} ${argument}`.trim();
  }
  // The two launches are named here because a prose request cannot ask for a form: with no argument the palette
  // hands over to the field, which is where the rest of a launch is decided anyway.
  if (verb.id === 'do') {
    if (!argument) { forms.focusRef(); return 'do'; }
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
      forms.openResume(argument);
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

ask.addEventListener('input', judge);
opener.onclick = toggle;

form.onsubmit = async (event) => {
  event.preventDefault();
  const parsed = parse(ask.value);
  // A verb typed ALONE is answered here: the model would be paid to map a line with no argument at all, and
  // would answer "unknown command" for a verb the page just completed. A verb WITH an argument that named no
  // task is prose ("ship the widget one") and stays tier 2's job.
  if (parsed && parsed.verb.takesTask && !parsed.task && !parsed.argument) {
    judge();
    return;
  }
  if (parsed && (!parsed.verb.takesTask || parsed.task)) {
    // Tier 1 answers for itself — `run` toasts what the server said, a report opens — so this is not the shared
    // submit: there is no one message to show and nothing to say while it happens.
    const button = form.querySelector('button[type=submit]');
    button.disabled = true;
    try {
      if (await runParsed(parsed)) {
        ask.value = '';
        close();
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
