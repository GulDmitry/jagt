import { make } from "../core/dom.js";
import { t } from "../core/i18n.js";
import * as store from "../core/store.js";
import * as rolelog from "./rolelog.js";

const roleLogs = /** @type {HTMLElement} */ (
  document.getElementById("role-logs")
);

/** @param {string} role */
const logChip = (role) => {
  const on = rolelog.open() === role;
  const node = make("button", on ? "chip log on" : "chip log", role);
  node.dataset.role = role;
  node.setAttribute("aria-pressed", String(on));
  node.dataset.tip = t("header.role-log.tip", { role });
  return node;
};

roleLogs.onclick = (event) => {
  const chip = /** @type {HTMLElement} */ (event.target).closest("[data-role]");
  if (chip instanceof HTMLElement) rolelog.toggle(chip.dataset.role ?? "");
};

export function render() {
  roleLogs.replaceChildren(...store.roleLogs().map(logChip));
}
