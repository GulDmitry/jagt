import { parse } from "../core/grammar.js";

// A verb typed ALONE is answered in place; one whose argument named no task is prose and stays the model's job.
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
