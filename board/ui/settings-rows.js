import { make } from "../core/dom.js";
import { t } from "../core/i18n.js";
import { choice } from "./choice.js";

/** @typedef {{ key: string, section: string, kind: string, options: string[], fallback: string | null, restart: boolean }} Setting */
/** @typedef {{ setting: Setting, value: unknown }} View */

/** @param {string} key */
export const labelOf = (key) => t(`settings.key.${key}`);

/**
 * @param {Setting} setting
 * @param {unknown} value
 */
const control = (setting, value) => {
  if (setting.kind === "BOOLEAN") {
    const box = Object.assign(document.createElement("input"), {
      type: "checkbox",
      checked: value === true || (value == null && setting.fallback === "true"),
    });
    box.setAttribute("role", "switch");
    return box;
  }
  return Object.assign(document.createElement("input"), {
    type: setting.kind === "INTEGER" ? "number" : "text",
    min: "0",
    placeholder: setting.fallback ?? "",
    value: Array.isArray(value) ? value.join(", ") : String(value ?? ""),
  });
};

/**
 * @param {Setting} setting
 * @param {HTMLInputElement} field
 */
const valueOf = (setting, field) => {
  if (setting.kind === "BOOLEAN") return field.checked;
  if (setting.kind === "INTEGER") return Number(field.value);
  if (setting.kind === "LIST")
    return field.value
      .split(",")
      .map((item) => item.trim())
      .filter(Boolean);
  return field.value;
};

/** @param {(setting: Setting, value: unknown) => void} change */
export const row =
  (change) =>
  /** @param {View} view */
  ({ setting, value }) => {
    if (setting.kind === "NAMES") return namesRow(setting, value);
    const hint = make(
      "small",
      "hint",
      t(value == null ? "settings.default" : "settings.set"),
    );
    const name = make("span", "", labelOf(setting.key));
    /** @type {HTMLElement} */
    let line;
    if (setting.kind === "CHOICE") {
      line = make(
        "div",
        "field",
        "",
        name,
        choice(
          setting.key,
          setting.options.map((option) => ({ value: option, label: option })),
          String(value ?? setting.fallback),
          (picked) => change(setting, picked),
        ),
        hint,
      );
    } else {
      const field = /** @type {HTMLInputElement} */ (control(setting, value));
      field.onchange = () => change(setting, valueOf(setting, field));
      line = make("label", "field", "", name, field, hint);
    }
    line.dataset.key = setting.key;
    return line;
  };

/**
 * @param {Setting} setting
 * @param {unknown} value
 */
const namesRow = (setting, value) => {
  const names = Array.isArray(value) ? value : [];
  const line = make(
    "div",
    "field",
    "",
    make("span", "", labelOf(setting.key)),
    make("span", "text", names.join(", ") || t("settings.none")),
  );
  line.dataset.key = setting.key;
  return line;
};
