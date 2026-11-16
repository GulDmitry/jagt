const read = (f, or = {}) =>
  Promise.all(
    ["i18n", "user/i18n"].map((d) =>
      fetch(`${d}/${f}.json`).then(
        (r) => (r.ok ? r.json() : or),
        () => or,
      ),
    ),
  );
const get = (code) => read(code).then(([a, b]) => ({ ...a, ...b }));

let code = "en";
try {
  code = localStorage.lang || code;
} catch {
  /* English */
}
const [english, chosen] = await Promise.all([get("en"), get(code)]);

const platform = {
  mod: /Mac|iPhone|iPad/.test(navigator.userAgent) ? "⌘" : "Ctrl+",
};

export const t = (key, values = {}) =>
  (chosen[key] ?? english[key] ?? key).replace(
    /\{(\w+)\}/g,
    (hole, name) => values[name] ?? platform[name] ?? hole,
  );

export const current = () => code;

export const choose = (next) => {
  try {
    localStorage.lang = next;
  } catch {
    /* until reload */
  }
};

export const languages = () =>
  read("index", []).then((l) =>
    Promise.all(
      [...new Set(l.flat())].sort().map((one) =>
        get(one).then((w) => ({
          code: one,
          name: w["language.name"] || one,
        })),
      ),
    ),
  );

// `data-t` is an element's text, `data-t-X` its attribute X (`tip` is data-tip).
document.documentElement.lang = code;
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
