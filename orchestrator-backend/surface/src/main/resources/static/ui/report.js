// A report's text as nodes: its links followable, a round's verdicts and the comments they answer told apart.

import {link, span} from '../core/dom.js';

// A url a human has to select and copy is one nobody follows. Only http(s) becomes an anchor: this text comes
// from an agent, a model and a hand-edited file, and a `javascript:` URL would run in the page that can deploy.
const WEB_LINK = /https?:\/\/[^\s<>"'`)\]]+/g;

// The two shapes a report writes: a numbered block head, and the comment a block answers.
const HEAD = /^(\s*\d+ · )([^·]+)(· .*)$/;
const QUOTE = /^\s*>/;
// A verdict with nothing wrong reads as muted, as every other verdict on the board does.
const VERDICT = {FIXED: 'ok', QUESTION: 'you'};

function linked(body) {
  const parts = [];
  let at = 0;
  for (const found of body.matchAll(WEB_LINK)) {
    // A URL ending a sentence keeps that sentence's punctuation out of the href.
    const url = found[0].replace(/[.,;:!?]+$/, '');
    if (found.index > at) parts.push(body.slice(at, found.index));
    parts.push(link(url, url));
    at = found.index + url.length;
  }
  parts.push(body.slice(at));
  return parts;
}

function marked(line) {
  const head = HEAD.exec(line);
  if (head) {
    const block = span('block', '');
    block.append(head[1], span(`verdict ${VERDICT[head[2].trim()] || 'muted'}`, head[2]), ...linked(head[3]));
    return [block];
  }
  if (!QUOTE.test(line)) {
    return linked(line);
  }
  const quote = span('quote', '');
  quote.append(...linked(line));
  return [quote];
}

export const body = (said) => said.trimEnd().split('\n')
  .flatMap((line, index) => (index ? ['\n', ...marked(line)] : marked(line)));
