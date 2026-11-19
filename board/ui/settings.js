import { api, refusal } from "../core/api.js";
import { make } from "../core/dom.js";
import { t } from "../core/i18n.js";
import { toast } from "./toast.js";
import { runGlobal } from "./verbs.js";

/** @typedef {{ key: string, section: string, kind: string, options: string[], restart: boolean }} Setting */
/** @typedef {{ setting: Setting, value: unknown }} View */

const SECTIONS = ["projects", "review", "tracker", "master", "notifications"];

const dialog = /** @type {HTMLDialogElement} */ (
  document.getElementById("settings-dialog")
);
const body = /** @type {HTMLElement} */ (
  document.getElementById("settings-body")
);
const restartBar = /** @type {HTMLElement} */ (
  document.getElementById("settings-restart")
);
/** @type {{ waiting: Set<string> }} */
const state = { waiting: new Set() };

/** @param {string} key */
const labelOf = (key) => t(`settings.key.${key}`);

/**
 * @param {Setting} setting
 * @param {unknown} value
 */
const control = (setting, value) => {
  if (setting.kind === "BOOLEAN") {
    return Object.assign(document.createElement("input"), {
      type: "checkbox",
      checked: value === true,
    });
  }
  if (setting.kind === "CHOICE") {
    const select = document.createElement("select");
    select.append(
      ...setting.options.map((option) =>
        Object.assign(document.createElement("option"), {
          value: option,
          textContent: option,
          selected: option === value,
        }),
      ),
    );
    return select;
  }
  return Object.assign(document.createElement("input"), {
    type: setting.kind === "INTEGER" ? "number" : "text",
    min: "0",
    value: Array.isArray(value) ? value.join(", ") : String(value ?? ""),
    placeholder: t("settings.default"),
  });
};

/**
 * @param {Setting} setting
 * @param {HTMLInputElement | HTMLSelectElement} field
 */
const valueOf = (setting, field) => {
  if (setting.kind === "BOOLEAN" && field instanceof HTMLInputElement)
    return field.checked;
  if (setting.kind === "INTEGER") return Number(field.value);
  if (setting.kind === "LIST")
    return field.value
      .split(",")
      .map((item) => item.trim())
      .filter(Boolean);
  return field.value;
};

const paintRestart = () => {
  restartBar.hidden = state.waiting.size === 0;
  /** @type {HTMLElement} */ (
    document.getElementById("settings-restart-text")
  ).textContent = t("settings.restart", {
    keys: [...state.waiting].join(", "),
  });
};

/**
 * @param {Setting} setting
 * @param {HTMLInputElement | HTMLSelectElement} field
 */
async function change(setting, field) {
  try {
    const result = await api("/api/settings", {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        key: setting.key,
        value: valueOf(setting, field),
      }),
    });
    toast(result.message);
    if (result.restart) {
      state.waiting.add(labelOf(setting.key));
      paintRestart();
    }
  } catch (e) {
    toast(refusal(e), true);
    await load();
  }
}

/** @param {View} view */
const row = ({ setting, value }) => {
  if (setting.kind === "NAMES") return namesRow(setting, value);
  const field = control(setting, value);
  field.onchange = () => change(setting, field);
  const label = make(
    "label",
    "field",
    "",
    make("span", "", labelOf(setting.key)),
    field,
  );
  label.dataset.key = setting.key;
  return label;
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

/** @param {unknown} value */
const projectRow = (value) => {
  const key = String(value);
  const edit = Object.assign(make("button", "", t("settings.edit")), {
    type: "button",
  });
  edit.onclick = () =>
    document.dispatchEvent(
      new CustomEvent("project-form", { detail: { key } }),
    );
  const line = make("div", "field-row", "", make("strong", "", key), edit);
  line.dataset.project = key;
  return line;
};

/** @param {View[]} views */
const paint = (views) => {
  body.replaceChildren(
    ...SECTIONS.map((name) => {
      const section = make(
        "section",
        "",
        "",
        make("h3", "", t(`settings.section.${name}`)),
      );
      section.dataset.section = name;
      const own = views.filter(({ setting }) => setting.section === name);
      if (name === "projects") {
        const names = own.flatMap(({ value }) =>
          Array.isArray(value) ? value : [],
        );
        const add = Object.assign(make("button", "", t("settings.add")), {
          type: "button",
        });
        add.id = "settings-add-project";
        add.onclick = () =>
          document.dispatchEvent(
            new CustomEvent("project-form", { detail: {} }),
          );
        section.append(...names.map(projectRow), add);
      } else {
        section.append(...own.map(row));
      }
      return section;
    }),
  );
};

async function load() {
  try {
    paint(await api("/api/settings"));
  } catch (e) {
    body.replaceChildren(
      make("p", "muted", t("settings.unread", { cause: refusal(e) })),
    );
  }
}

document.addEventListener("settings-open", () => {
  if (!dialog.open) dialog.showModal();
  load();
});
document
  .getElementById("project-dialog")
  ?.addEventListener("close", () => dialog.open && load());
/** @type {HTMLElement} */ (document.getElementById("close-settings")).onclick =
  () => dialog.close();
/** @type {HTMLElement} */ (
  document.getElementById("settings-restart-go")
).onclick = () => runGlobal("restart");
