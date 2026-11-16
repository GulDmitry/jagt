import { api } from "../core/api.js";
import * as store from "../core/store.js";
import { run } from "./act.js";
import { openReport } from "./dialogs.js";
import { toast } from "./toast.js";

// A line answered without creating anything would only repeat the attempt.
const HANDLED = { clear: true };
const KEPT = { clear: false };

// A line that parses is executed; only free text is worth a model call.
export async function execute(parsed, partFor) {
  const { verb, argument, task, project } = parsed;
  if (verb.takesTask) {
    await run(task.id, verb.id, project);
    return HANDLED;
  }
  const part = partFor(verb.part);
  // What was typed after the verb goes with it: a report that narrows to one task must not answer for all.
  if (verb.report) {
    const narrowed = argument ? `?about=${encodeURIComponent(argument)}` : "";
    const opened = await openReport(
      `${verb.id} ${store.nameOf(argument)}`.trim(),
      `/api/commands/${encodeURIComponent(verb.id)}${narrowed}`,
      { about: verb.aboutOneTask ? argument : null, extra: part.section?.() },
    );
    // A report that could not be read leaves the typed line where it was, to try again.
    return opened ? HANDLED : KEPT;
  }
  // Typed alone, a verb with a part of the board hands over to it, where the rest of its inputs are chosen.
  if (!argument && part.focus) {
    part.focus();
    return HANDLED;
  }
  if (argument && (await part.argued?.(argument))) return HANDLED;
  if (verb.confirmation && !confirm(verb.confirmation)) return KEPT;
  const narrowed = argument ? `?about=${encodeURIComponent(argument)}` : "";
  const result = await api(
    `/api/commands/${encodeURIComponent(verb.id)}${narrowed}`,
    { method: "POST" },
  );
  toast(result.message);
  return HANDLED;
}
