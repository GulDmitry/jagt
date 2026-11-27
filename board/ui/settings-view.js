import { make } from "../core/dom.js";
import { t } from "../core/i18n.js";
import { row } from "./settings-rows.js";

export { labelOf } from "./settings-rows.js";

/** @typedef {import("./settings-rows.js").View} View */

/** @type {{ tab: string }} */
const state = { tab: "" };

/** @param {unknown} value */
const projectRow = (value) => {
  const key = String(value);
  const edit = Object.assign(make("button", "", t("settings.edit")), {
    type: "button",
  });
  edit.setAttribute("aria-label", t("settings.edit.label", { key }));
  edit.onclick = () =>
    document.dispatchEvent(
      new CustomEvent("project-form", { detail: { key } }),
    );
  const line = make("div", "field-row", "", make("strong", "", key), edit);
  line.dataset.project = key;
  return line;
};

/** @param {string} name */
const sectionName = (name) => t(`settings.section.${name}`);

/** @type {Record<string, number>} */
const steps = { ArrowDown: 1, ArrowUp: -1 };

/**
 * @param {HTMLElement} body
 * @param {HTMLElement} tab
 */
const select = (body, tab) => {
  state.tab = tab.dataset.section ?? "";
  body.querySelectorAll("[role=tab]").forEach((one) => {
    const on = one === tab;
    one.setAttribute("aria-selected", String(on));
    one.setAttribute("tabindex", on ? "0" : "-1");
  });
  body.querySelectorAll("[role=tabpanel]").forEach((pane) => {
    /** @type {HTMLElement} */ (pane).hidden =
      /** @type {HTMLElement} */ (pane).dataset.section !== state.tab;
  });
};

/**
 * @param {string} name
 * @param {View[]} own
 * @param {Parameters<typeof row>[0]} change
 */
const pane = (name, own, change) => {
  const section = make("section", "", "", make("h3", "", sectionName(name)));
  section.dataset.section = name;
  section.id = `settings-pane-${name}`;
  section.setAttribute("role", "tabpanel");
  section.setAttribute("aria-labelledby", `settings-tab-${name}`);
  if (name !== "projects") {
    section.append(...own.map(row(change)));
    return section;
  }
  const names = own.flatMap(({ value }) => (Array.isArray(value) ? value : []));
  const add = Object.assign(make("button", "", t("settings.add")), {
    type: "button",
  });
  add.id = "settings-add-project";
  add.onclick = () =>
    document.dispatchEvent(new CustomEvent("project-form", { detail: {} }));
  section.append(...names.map(projectRow), add);
  return section;
};

/** @param {HTMLElement} body */
const tabFor =
  (body) =>
  /** @param {string} name */
  (name) => {
    const tab = Object.assign(make("button", "", sectionName(name)), {
      type: "button",
      id: `settings-tab-${name}`,
    });
    tab.dataset.section = name;
    tab.setAttribute("role", "tab");
    tab.setAttribute("aria-controls", `settings-pane-${name}`);
    tab.onclick = () => select(body, tab);
    return tab;
  };

/**
 * @param {HTMLElement} body
 * @param {View[]} views
 * @param {Parameters<typeof row>[0]} change
 */
export const paint = (body, views, change) => {
  const names = [...new Set(views.map(({ setting }) => setting.section))];
  const list = make("div", "tabs", "", ...names.map(tabFor(body)));
  list.setAttribute("role", "tablist");
  list.setAttribute("aria-orientation", "vertical");
  list.onkeydown = (event) => {
    const step = steps[event.key] ?? 0;
    if (!step) return;
    event.preventDefault();
    const tabs = [...list.querySelectorAll("button")];
    const at = tabs.findIndex((tab) => tab === event.target);
    const next = tabs[(at + step + tabs.length) % tabs.length];
    select(body, next);
    next.focus();
  };
  body.replaceChildren(
    list,
    ...names.map((name) =>
      pane(
        name,
        views.filter(({ setting }) => setting.section === name),
        change,
      ),
    ),
  );
  const first = list.querySelector("button");
  select(
    body,
    /** @type {HTMLElement} */ (
      list.querySelector(`[data-section="${state.tab}"]`) ?? first
    ),
  );
};

/**
 * @param {HTMLElement} body
 * @param {string} cause
 */
export const unread = (body, cause) =>
  body.replaceChildren(make("p", "muted", t("settings.unread", { cause })));
