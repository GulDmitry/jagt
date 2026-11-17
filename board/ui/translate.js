import { current, t } from "../core/i18n.js";

// `data-t` is an element's text, `data-t-X` its attribute X (`tip` is data-tip).
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
