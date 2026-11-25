/** @typedef {{ seq: number, time: string, level: string, text: string[] }} Entry */

const ENTRY =
  /^(?:\d{4}-)?(?:\d\d-\d\d )?(\d\d:\d\d:\d\d)Z? ([A-Z]{4,5}) (.*)$/;

/** @param {string} level */
export const broken = (level) => level === "WARN" || level === "ERROR";

/**
 * Entries newest first, each holding its own wrapped lines; `seq` counts from the oldest and stays put as newer
 * ones arrive; `head` is what precedes the first entry.
 * @param {string} said
 * @returns {{ head: string[], entries: Entry[] }}
 */
export function parse(said) {
  /** @type {string[]} */
  const head = [];
  /** @type {Entry[]} */
  const entries = [];
  for (const line of said.split("\n")) {
    const match = ENTRY.exec(line);
    if (match) {
      entries.push({
        seq: entries.length,
        time: match[1],
        level: match[2],
        text: [match[3]],
      });
    } else if (entries.length) {
      entries[entries.length - 1].text.push(line);
    } else {
      head.push(line);
    }
  }
  return { head, entries: entries.reverse() };
}
