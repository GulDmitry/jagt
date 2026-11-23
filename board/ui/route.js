import { parse } from "../core/grammar.js";

/** @param {string} line */
export const routeLine = (line) => {
  const parsed = parse(line);
  if (parsed && parsed.verb.takesTask && !parsed.task && !parsed.argument) {
    return { route: "judge", parsed };
  }
  if (parsed && (!parsed.verb.takesTask || parsed.task)) {
    return { route: "run", parsed };
  }
  return { route: "interpret", parsed };
};
