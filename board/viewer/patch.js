/**
 * @typedef {{ start: number, count: number, lines: string[] }} Hunk
 * @typedef {{ path: string, added: number, removed: number, hunks: Hunk[], gone: boolean }} Changed
 */

const HUNK = /^@@ -\d+(?:,\d+)? \+(\d+)(?:,(\d+))? @@/;
const HEADER = /^diff --\w+ /;

/**
 * @param {string} line
 * @returns {Changed}
 */
const fileOf = (line) => ({
  path: line.slice(line.lastIndexOf(" b/") + 3),
  added: 0,
  removed: 0,
  hunks: [],
  gone: false,
});

/**
 * @param {string} line
 * @returns {Hunk}
 */
const hunkOf = (line) => {
  const [, start, count] = HUNK.exec(line) ?? [];
  return {
    start: Number(start),
    count: count === undefined ? 1 : Number(count),
    lines: [],
  };
};

/**
 * @param {Changed} one
 * @param {string} line
 */
const target = (one, line) => {
  one.gone = line === "+++ /dev/null";
  if (!one.gone) one.path = line.slice(6);
};

/**
 * @param {Changed} one
 * @param {Hunk} hunk
 * @param {string} line
 */
const counted = (one, hunk, line) => {
  hunk.lines.push(line);
  if (line[0] === "+") one.added++;
  if (line[0] === "-") one.removed++;
};

// One unified diff as its files: path, the lines each side counts, and the hunks that rebuild the old text.
/**
 * @param {string} diff
 * @returns {Changed[]}
 */
export function files(diff) {
  /** @type {Changed[]} */
  const out = [];
  /** @type {Changed | null} */
  let one = null;
  /** @type {Hunk | null} */
  let hunk = null;
  for (const line of diff.split("\n")) {
    if (HEADER.test(line)) {
      one = fileOf(line);
      hunk = null;
      out.push(one);
    } else if (!one) {
      continue;
    } else if (!hunk && line.startsWith("+++ ")) {
      target(one, line);
    } else if (HUNK.test(line)) {
      hunk = hunkOf(line);
      one.hunks.push(hunk);
    } else if (hunk && /^[ +-]/.test(line)) {
      counted(one, hunk, line);
    }
  }
  return out;
}

// The text before the change, from the text after it and the hunks.
/**
 * @param {string} current
 * @param {Hunk[]} hunks
 */
export function original(current, hunks) {
  const now = current.split("\n");
  /** @type {string[]} */
  const out = [];
  let at = 0;
  for (const hunk of hunks) {
    const from = hunk.count === 0 ? hunk.start : hunk.start - 1;
    out.push(...now.slice(at, from));
    at = from;
    for (const line of hunk.lines) {
      if (line[0] !== "+") out.push(line.slice(1));
      if (line[0] !== "-") at++;
    }
  }
  out.push(...now.slice(at));
  return out.join("\n");
}
