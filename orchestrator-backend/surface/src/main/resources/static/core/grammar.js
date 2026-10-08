// Tier 1: a typed line understood WITHOUT a model — a known verb and, for the per-task ones, a task that exists.

import * as store from './store.js';

// A retired spelling is accepted and never offered: two spellings on screen are two answers to one question.
function verbFor(word) {
  const typed = word.toLowerCase();
  // Its own name first, exactly as the server resolves it: an alias must never shadow another verb's id.
  return store.verbs().find((verb) => verb.id === typed)
    || store.verbs().find((verb) => (verb.aliases || []).includes(typed));
}

export function parse(line) {
  const tokens = line.trim().split(/\s+/).filter(Boolean);
  if (!tokens.length) return null;
  const verb = verbFor(tokens[0]);
  if (!verb) return null;
  const argument = tokens.slice(1).join(' ');
  if (!verb.takesTask) return {verb, argument};
  return {verb, argument, task: store.taskFor(argument)};
}

// A typo must be visible before Run, not after a model has been paid to guess at it.
export function verdictOf(line) {
  if (!line) return {text: ''};
  const parsed = parse(line);
  if (!parsed) {
    const word = line.split(/\s+/)[0];
    return {text: verbFor(word) ? '' : `“${word}” is not a command — this will go to the model as plain words`};
  }
  if (parsed.verb.takesTask && !parsed.task) {
    return {
      kind: 'bad',
      text: parsed.argument
        ? `no task “${parsed.argument}” — use a ticket id or its alias`
        : `${parsed.verb.id} needs a task: ${parsed.verb.id} <ticket|alias>`,
    };
  }
  return {kind: 'ok', text: `runs as typed — ${parsed.verb.hint}`};
}
