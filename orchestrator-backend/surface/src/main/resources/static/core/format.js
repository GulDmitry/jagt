// Formatted exactly as the server prints them: one elapsed time must not read "1h" here and "2h" there.

import {full} from './time.js';

export const duration = (millis) => {
  const minutes = Math.max(0, Math.floor(millis / 60000));
  if (minutes < 60) return `${minutes}m`;
  if (minutes < 1440) return `${Math.floor(minutes / 60)}h`;
  return `${Math.floor(minutes / 1440)}d`;
};

// Seconds matter while the wait is under a minute: a countdown is watched ticking.
export const countdown = (millis) => {
  const seconds = Math.max(0, Math.floor(millis / 1000));
  if (seconds < 60) return `${seconds}s`;
  // CEILING: a ten-minute wait must not read "9m" for its first minute.
  const minutes = Math.ceil(seconds / 60);
  return minutes < 60 ? `${minutes}m` : `${Math.ceil(minutes / 60)}h`;
};

// A task by both its names, the short one first; a task with no alias is its id.
export const bothNames = (task) => (task.alias ? `${task.alias} \u00b7 ${task.id}` : task.id);

// A report's text as data: its links followable, a round's verdicts and the comments they answer told apart.
// Only http(s) becomes a link: this text comes from an agent, a model and a hand-edited file.
const WEB_LINK = /https?:\/\/[^\s<>"'`)\]]+/g;
// The two shapes a report writes: a numbered block head, and the comment a block answers.
const HEAD = /^(\s*\d+ · )([^·]+)(· .*)$/;
const QUOTE = /^\s*>/;
// A verdict with nothing wrong reads as muted, as every other verdict on the board does.
const TONE = {FIXED: 'ok', QUESTION: 'you'};

function linked(line) {
  const parts = [];
  let at = 0;
  for (const found of line.matchAll(WEB_LINK)) {
    // A URL ending a sentence keeps that sentence's punctuation out of the href.
    const url = found[0].replace(/[.,;:!?]+$/, '');
    if (found.index > at) parts.push({kind: 'text', text: line.slice(at, found.index)});
    parts.push({kind: 'link', text: url, href: url});
    at = found.index + url.length;
  }
  parts.push({kind: 'text', text: line.slice(at)});
  return parts;
}

function marked(line) {
  const head = HEAD.exec(line);
  if (head) {
    return [{kind: 'block', parts: [{kind: 'text', text: head[1]},
      {kind: 'verdict', text: head[2], tone: TONE[head[2].trim()] || 'muted'}, ...linked(head[3])]}];
  }
  return QUOTE.test(line) ? [{kind: 'quote', parts: linked(line)}] : linked(line);
}

export const reportParts = (said) => said.trimEnd().split('\n')
  .flatMap((line, index) => (index ? [{kind: 'text', text: '\n'}, ...marked(line)] : marked(line)));

// What the session judged on each switch, one `name: yes — why` line apiece; empty where it judged none.
export const switchLines = (task) => Object.entries(task.switches || {}).map(([name, said]) => `${name}: ${said}`);

// What jagt saw when it stopped the task, its advice, and when; the reason on the line is the server's.
export const stopLines = (task) => (task.stop ? [task.stop.evidence, task.stop.next, `stopped ${full(task.stop.at)}`] : []);
