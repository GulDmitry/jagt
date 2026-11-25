import { choose, current, languages, t } from "../core/i18n.js";
import { choice } from "./choice.js";
import "./settings.js";

const gear = /** @type {HTMLElement} */ (document.getElementById("gear"));
const dialog = /** @type {HTMLDialogElement} */ (
  document.getElementById("gear-dialog")
);
const root = document.documentElement;

/** @param {string} chosen */
const paint = (chosen) => {
  if (chosen === "auto") delete root.dataset.theme;
  else root.dataset.theme = chosen;
};

/** @param {string} chosen */
const pickTheme = (chosen) => {
  try {
    if (chosen === "auto") localStorage.removeItem("theme");
    else localStorage.theme = chosen;
  } catch {
    // the choice then lasts until reload
  }
  paint(chosen);
};

/**
 * @param {string} id
 * @param {Node} control
 */
const into = (id, control) =>
  /** @type {HTMLElement} */ (document.getElementById(id)).append(control);

const stored = root.dataset.theme;
into(
  "gear-appearance",
  choice(
    "theme",
    ["auto", "light", "dark"].map((value) => ({
      value,
      label: t(`theme.${value}`),
    })),
    stored === "light" || stored === "dark" ? stored : "auto",
    pickTheme,
  ),
);

const offered = languages().then((found) =>
  into(
    "gear-language",
    choice(
      "lang",
      found.map(({ code, name }) => ({ value: code, label: name })),
      current(),
      (code) => {
        choose(code);
        window.location.reload();
      },
    ),
  ),
);
fetch("/api/session")
  .then((response) => response.json())
  .then(({ login }) => {
    /** @type {HTMLElement} */ (document.getElementById("gear-logout")).hidden =
      !login;
  })
  .catch(() => {});
gear.onclick = () =>
  offered.then(() => {
    dialog.showModal();
    gear.setAttribute("aria-expanded", "true");
  });
dialog.addEventListener("close", () => {
  gear.setAttribute("aria-expanded", "false");
  gear.focus();
});
dialog.onclick = ({ target }) => {
  if (target === dialog) dialog.close();
  if (!(target instanceof HTMLElement)) return;
  if (target.id === "close-gear") dialog.close();
  if (target.id === "gear-logout") {
    fetch("/logout", { method: "POST" }).finally(() =>
      window.location.assign("/"),
    );
  }
  if (target.id === "gear-settings") {
    dialog.close();
    document.dispatchEvent(new Event("settings-open"));
  }
};
