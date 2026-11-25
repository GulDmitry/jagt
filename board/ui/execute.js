import { api } from "../core/api.js";
import * as store from "../core/store.js";
import { run } from "./act.js";
import { openReport } from "./dialogs.js";
import { toast } from "./toast.js";

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

/**
 * @param {Verb} verb
 * @param {string} argument
 * @param {Part} part
 */
async function report(verb, argument, part) {
  const opened = await openReport(
    `${verb.id} ${store.nameOf(argument)}`.trim(),
    commandUrl(verb, argument),
    {
      about: verb.aboutOneTask ? argument : null,
      extra: part.section?.(),
      log: verb.id === "tail",
    },
  );
  return opened ? HANDLED : KEPT;
}

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
