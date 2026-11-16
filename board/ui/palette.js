import { parse } from "../core/grammar.js";
import { interpret } from "./interpret.js";
import { boardArrived, verbsArrived } from "./refresh.js";
import { tier1 } from "./tier1.js";
import { ask, judge } from "./verdict.js";

const form = document.getElementById("palette");
const opener = document.getElementById("open-palette");

// Asked here, decided at wiring time, so this module knows no form and no verb by name.
const state = {
  forms: { partFor: () => ({}) },
};

export const wire = (wired) => {
  state.forms = wired;
};

export const isOpen = () => !form.hidden;

function show(open) {
  form.hidden = !open;
  opener.setAttribute("aria-pressed", String(open));
  if (open) {
    ask.focus();
    ask.select();
  }
}

export const close = () => {
  const wasOpen = isOpen();
  show(false);
  return wasOpen;
};

export function toggle() {
  show(form.hidden);
  judge();
}

const clear = () => {
  ask.value = "";
  close();
};

ask.addEventListener("input", judge);
opener.onclick = toggle;

form.onsubmit = async (event) => {
  event.preventDefault();
  await Promise.all([verbsArrived, boardArrived]);
  const parsed = parse(ask.value);
  // A verb typed ALONE is answered here; one whose argument named no task is prose and stays tier 2's job.
  if (parsed && parsed.verb.takesTask && !parsed.task && !parsed.argument) {
    judge();
    return;
  }
  if (parsed && (!parsed.verb.takesTask || parsed.task)) {
    await tier1(parsed, { form, partFor: state.forms.partFor, clear });
    return;
  }
  await interpret(form, ask.value, clear);
};
