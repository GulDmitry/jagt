import { full } from "./time.js";

/** @param {number} millis */
export const duration = (millis) => {
  const minutes = Math.max(0, Math.floor(millis / 60000));
  if (minutes < 60) return `${minutes}m`;
  if (minutes < 1440) return `${Math.floor(minutes / 60)}h`;
  return `${Math.floor(minutes / 1440)}d`;
};

// Seconds matter while the wait is under a minute: a countdown is watched ticking.
/** @param {number} millis */
export const countdown = (millis) => {
  const seconds = Math.max(0, Math.floor(millis / 1000));
  if (seconds < 60) return `${seconds}s`;
  // CEILING: a ten-minute wait must not read "9m" for its first minute.
  const minutes = Math.ceil(seconds / 60);
  return minutes < 60 ? `${minutes}m` : `${Math.ceil(minutes / 60)}h`;
};

/** @param {{ origin?: string, role?: string }} step */
export const originLabel = (step) =>
  (step.origin === "ACCEPTANCE" && step.role ? step.role : step.origin || "")
    .toLowerCase()
    .replace("_", "-");

/** @param {{ id: string, alias?: string }} task */
export const bothNames = (task) =>
  task.alias ? `${task.alias} \u00b7 ${task.id}` : task.id;

// Only http(s) becomes a link: this text comes from an agent, a model and a hand-edited file.
const WEB_LINK = /https?:\/\/[^\s<>"'`)\]]+/g;
const HEAD = /^(\s*\d+ · )([^·]+)(· .*)$/;
const QUOTE = /^\s*>/;
/** @type {Record<string, string>} */
const TONE = { FIXED: "ok", QUESTION: "you" };

/**
 * @typedef {{ kind: string, text?: string, href?: string, tone?: string, parts?: Part[] }} Part
 */

/**
 * @param {string} line
 * @returns {Part[]}
 */
function linked(line) {
  /** @type {Part[]} */
  const parts = [];
  let at = 0;
  for (const found of line.matchAll(WEB_LINK)) {
    // A URL ending a sentence keeps that sentence's punctuation out of the href.
    const url = found[0].replace(/[.,;:!?]+$/, "");
    if (found.index > at)
      parts.push({ kind: "text", text: line.slice(at, found.index) });
    parts.push({ kind: "link", text: url, href: url });
    at = found.index + url.length;
  }
  parts.push({ kind: "text", text: line.slice(at) });
  return parts;
}

/**
 * @param {string} line
 * @returns {Part[]}
 */
function marked(line) {
  const head = HEAD.exec(line);
  if (head) {
    return [
      {
        kind: "block",
        parts: [
          { kind: "text", text: head[1] },
          {
            kind: "verdict",
            text: head[2],
            tone: TONE[head[2].trim()] || "muted",
          },
          ...linked(head[3]),
        ],
      },
    ];
  }
  return QUOTE.test(line)
    ? [{ kind: "quote", parts: linked(line) }]
    : linked(line);
}

// A task still on the board opens from a line naming it.
/** @param {string[]} ids */
const named = (ids) =>
  new RegExp(
    `(?<![\\w-])(${ids.map((id) => id.replace(/\W/g, "\\$&")).join("|")})(?![\\w-])`,
  );
/**
 * @param {Part} part
 * @param {string[]} ids
 * @returns {Part[]}
 */
const tasked = (part, ids) =>
  part.kind !== "text" || !ids.length
    ? [part]
    : (part.text ?? "")
        .split(named(ids))
        .map((text, at) => ({ kind: at % 2 ? "task" : "text", text }));

/**
 * @param {string} said
 * @param {string[]} [ids]
 * @returns {Part[]}
 */
export const reportParts = (said, ids = []) =>
  said
    .trimEnd()
    .split("\n")
    .flatMap((line, index) =>
      index ? [{ kind: "text", text: "\n" }, ...marked(line)] : marked(line),
    )
    .flatMap((part) => tasked(part, ids));

/** @param {{ switches?: Record<string, string> }} task */
export const switchLines = (task) =>
  Object.entries(task.switches || {}).map(([name, said]) => `${name}: ${said}`);

/**
 * @param {{ stop?: { evidence?: string, next?: string, at: number, triage?: string } }} task
 * @returns {string[]}
 */
export const stopLines = (task) =>
  task.stop
    ? [
        task.stop.evidence,
        task.stop.next,
        `stopped ${full(task.stop.at)}`,
        task.stop.triage,
      ].flatMap((line) => (line ? [line] : []))
    : [];
