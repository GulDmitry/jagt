import { api } from "../core/api.js";

import { t } from "../core/i18n.js";
import * as store from "../core/store.js";
import * as projects from "./projects.js";
import { submits } from "./submit.js";

const dialog = /** @type {HTMLDialogElement} */ (
  document.getElementById("launch-dialog")
);
const form = /** @type {HTMLFormElement} */ (document.getElementById("launch"));
const ref = /** @type {HTMLInputElement} */ (document.getElementById("ref"));
const strategy = /** @type {HTMLSelectElement} */ (
  document.getElementById("strategy")
);

export const open = () => {
  if (!dialog.open) dialog.showModal();
  ref.focus();
  projects.cards();
};

/** @type {HTMLElement} */ (document.getElementById("close-launch")).onclick =
  () => dialog.close();

form.addEventListener("keydown", (event) => {
  if (event.key === "Enter" && (event.metaKey || event.ctrlKey)) {
    event.preventDefault();
    form.requestSubmit();
  }
});

/** @type {{ rendered: string | null }} */
const state = {
  rendered: null,
};

export function render() {
  const choices = store.branchStrategies();
  const signature = choices.map((choice) => choice.id).join("\n");
  if (state.rendered === signature) return;
  state.rendered = signature;
  const chosenBefore = strategy.value;
  strategy.replaceChildren(
    ...choices.map((choice) => new Option(choice.id, choice.id)),
  );
  strategy.dataset.tip = choices
    .map((choice) => `${choice.id} — ${choice.hint}`)
    .join(" · ");
  if (choices.some((choice) => choice.id === chosenBefore)) {
    strategy.value = chosenBefore;
  }
}

const plan = /** @type {HTMLInputElement} */ (document.getElementById("plan"));
const notes = /** @type {HTMLInputElement} */ (
  document.getElementById("notes")
);

const problem = () => {
  const ticket = ref.value.trim();
  if (/\s/.test(ticket)) return t("launch.problem.words");
  if (!ticket && !notes.value.trim()) return t("launch.problem.empty");
  if (!ticket && !projects.chosen() && store.projects().length !== 1)
    return t("launch.problem.notes");
  return projects.chosen() || plan.checked ? null : t("launch.problem.project");
};

form.addEventListener("change", () =>
  /** @type {HTMLElement} */ (
    document.getElementById("launch-state")
  ).classList.remove("bad"),
);

submits(form, {
  problem,
  waiting: t("launch.starting"),
  send: () =>
    api("/api/tasks", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        ref: ref.value,
        project: projects.chosen(),
        mode: plan.checked ? "plan" : null,
        strategy: strategy.value,
        baseBranch: /** @type {HTMLInputElement} */ (
          document.getElementById("base-branch")
        ).value,
        notes: notes.value,
      }),
    }),
  done: () => {
    form.reset();
    projects.forget();
    dialog.close();
  },
});
