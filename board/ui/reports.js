import * as store from "../core/store.js";
import { onJobsClick } from "./chips.js";
import { openReport } from "./dialogs.js";
import * as menu from "./menu.js";
import { parts } from "./parts.js";

const openVerb = (id) => {
  const verb = store.verbs().find((each) => each.id === id);
  return openReport(verb ? `${id} — ${verb.hint}` : id, `/api/commands/${id}`, {
    extra: parts[verb?.part]?.section?.(),
  });
};

menu.onPick(openVerb);
onJobsClick(() => openVerb("jobs"));
