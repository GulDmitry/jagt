import { current, t } from "../core/i18n.js";

document.documentElement.lang = current();
document.querySelectorAll("*").forEach((node) =>
  [...node.attributes].forEach(({ name, value }) => {
    if (name === "data-t") node.textContent = t(value);
    else if (name.startsWith("data-t-"))
      node.setAttribute(
        name === "data-t-tip" ? "data-tip" : name.slice(7),
        t(value),
      );
  }),
);
