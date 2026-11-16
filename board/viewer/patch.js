const HUNK = /^@@ -\d+(?:,\d+)? \+(\d+)(?:,(\d+))? @@/;
const HEADER = /^diff --\w+ /;

// One unified diff as its files: path, the lines each side counts, and the hunks that rebuild the old text.
export function files(diff) {
  const out = [];
  let one = null;
  let hunk = null;
  for (const line of diff.split('\n')) {
    if (HEADER.test(line)) {
      one = {path: line.slice(line.lastIndexOf(' b/') + 3), added: 0, removed: 0, hunks: [], gone: false};
      hunk = null;
      out.push(one);
    } else if (!one) {
      continue;
    } else if (!hunk && line.startsWith('+++ ')) {
      one.gone = line === '+++ /dev/null';
      if (!one.gone) one.path = line.slice(6);
    } else if (HUNK.test(line)) {
      const [, start, count] = HUNK.exec(line);
      hunk = {start: Number(start), count: count === undefined ? 1 : Number(count), lines: []};
      one.hunks.push(hunk);
    } else if (hunk && /^[ +-]/.test(line)) {
      hunk.lines.push(line);
      if (line[0] === '+') one.added++;
      if (line[0] === '-') one.removed++;
    }
  }
  return out;
}

// The text before the change, from the text after it and the hunks.
export function original(current, hunks) {
  const now = current.split('\n');
  const out = [];
  let at = 0;
  for (const hunk of hunks) {
    const from = hunk.count === 0 ? hunk.start : hunk.start - 1;
    out.push(...now.slice(at, from));
    at = from;
    for (const line of hunk.lines) {
      if (line[0] !== '+') out.push(line.slice(1));
      if (line[0] !== '-') at++;
    }
  }
  out.push(...now.slice(at));
  return out.join('\n');
}
