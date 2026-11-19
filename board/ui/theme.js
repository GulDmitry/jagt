import { make, within } from "../core/dom.js";
import { choose, current, languages, t } from "../core/i18n.js";
import "./settings.js";

const gear = /** @type {HTMLElement} */ (document.getElementById("gear"));
const list = /** @type {HTMLElement} */ (document.getElementById("gear-menu"));
const root = document.documentElement;

/** @param {string} choice */
const paint = (choice) => {
  if (choice === "auto") delete root.dataset.theme;
  else root.dataset.theme = choice;
  list
    .querySelectorAll("button[value]")
    .forEach(
      (button) =>
        button instanceof HTMLButtonElement &&
        button.setAttribute("aria-pressed", String(button.value === choice)),
    );
};

/** @param {boolean} open */
const shut = (open) => {
  list.hidden = !open;
  gear.setAttribute("aria-expanded", String(open));
};

list.append(
  ...["auto", "light", "dark"].map((choice) =>
    Object.assign(make("button", "", t(`theme.${choice}`)), { value: choice }),
  ),
);
const offer = () =>
  languages().then((offered) =>
    list.append(
      ...offered.map(({ code, name }) => {
        const button = make("button", "", name);
        button.dataset.lang = code;
        button.setAttribute("aria-pressed", String(code === current()));
        return button;
      }),
    ),
  );
fetch("/api/session")
  .then((response) => response.json())
  .then(({ login }) => {
    /** @type {HTMLElement} */ (document.getElementById("gear-logout")).hidden =
      !login;
  })
  .catch(() => {});
gear.addEventListener("click", offer, { once: true });
gear.onclick = () => shut(Boolean(list.hidden));
list.onclick = ({ target }) => {
  if (!(target instanceof HTMLElement)) return;
  if (target.id === "gear-logout") {
    fetch("/logout", { method: "POST" }).finally(() =>
      window.location.assign("/"),
    );
    return;
  }
  if (target.id === "gear-settings") {
    shut(false);
    document.dispatchEvent(new Event("settings-open"));
    return;
  }
  if (target.dataset.lang) {
    choose(target.dataset.lang);
    window.location.reload();
    return;
  }
  const choice = target instanceof HTMLButtonElement ? target.value : "";
  if (!choice) return;
  try {
    if (choice === "auto") localStorage.removeItem("theme");
    else localStorage.theme = choice;
  } catch {
    // the choice then lasts until reload
  }
  paint(choice);
  shut(false);
};
document.addEventListener(
  "pointerdown",
  (event) => within(event.target, ".menu") || shut(false),
);
const stored = root.dataset.theme;
paint(stored === "light" || stored === "dark" ? stored : "auto");
