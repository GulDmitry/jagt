import { api } from "../core/api.js";
import * as store from "../core/store.js";
import { run } from "./act.js";
import { openReport } from "./dialogs.js";
import { toast } from "./toast.js";

// A line answered without creating anything would only repeat the attempt.
const HANDLED = { clear: true };
const KEPT = { clear: false };

/** @typedef {{ focus?: () => void, argued?: (argument: string) => Promise<boolean>, section?: () => HTMLElement }} Part */
/** @typedef {import("../core/store.js").Verb} Verb */
/** @typedef {NonNullable<ReturnType<typeof import("../core/grammar.js").parse>>} Parsed */

/**
 * @param {Verb} verb
 * @param {string} argument
 */
const commandUrl = (verb, argument) => {
  const narrowed = argument ? `?about=${encodeURIComponent(argument)}` : "";
  return `/api/commands/${encodeURIComponent(verb.id)}${narrowed}`;
};

// What was typed after the verb goes with it: a report that narrows to one task must not answer for all.
/**
 * @param {Verb} verb
 * @param {string} argument
 * @param {Part} part
 */
async function report(verb, argument, part) {
  const opened = await openReport(
    `${verb.id} ${store.nameOf(argument)}`.trim(),
    commandUrl(verb, argument),
    { about: verb.aboutOneTask ? argument : null, extra: part.section?.() },
  );
  // A report that could not be read leaves the typed line where it was, to try again.
  return opened ? HANDLED : KEPT;
}

// A line that parses is executed; only free text is worth a model call.
/**
 * @param {Parsed} parsed
 * @param {(part: string | undefined) => Part} partFor
 */
export async function execute(parsed, partFor) {
  const { verb, argument, task, project } = parsed;
  if (verb.takesTask) {
    if (!task) throw new Error(`${verb.id} names no task`);
    await run(task.id, verb.id, project);
    return HANDLED;
  }
  const part = partFor(verb.part);
  if (verb.report) return report(verb, argument, part);
  // Typed alone, a verb with a part of the board hands over to it, where the rest of its inputs are chosen.
  if (!argument && part.focus) {
    part.focus();
    return HANDLED;
  }
  if (argument && (await part.argued?.(argument))) return HANDLED;
  if (verb.confirmation && !confirm(verb.confirmation)) return KEPT;
  const result = await api(commandUrl(verb, argument), { method: "POST" });
  toast(result.message);
  return HANDLED;
}
