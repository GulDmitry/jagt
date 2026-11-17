/**
 * @param {string} f
 * @param {any} [or]
 * @returns {Promise<any[]>}
 */
const read = (f, or = {}) =>
  Promise.all(
    ["i18n", "user/i18n"].map((d) =>
      fetch(`${d}/${f}.json`).then(
        (r) => (r.ok ? r.json() : or),
        () => or,
      ),
    ),
  );
/** @param {string} code @returns {Promise<Record<string, string>>} */
const get = (code) => read(code).then(([a, b]) => ({ ...a, ...b }));

const chosenLanguage = { code: "en" };
try {
  chosenLanguage.code = localStorage.lang || chosenLanguage.code;
} catch {
  /* English */
}
const [english, chosen] = await Promise.all([
  get("en"),
  get(chosenLanguage.code),
]);

/** @type {Record<string, string>} */
const platform = {
  mod: /Mac|iPhone|iPad/.test(navigator.userAgent) ? "⌘" : "Ctrl+",
};

/**
 * @param {string} key
 * @param {Record<string, string | number | undefined>} [values]
 */
export const t = (key, values = {}) =>
  (chosen[key] ?? english[key] ?? key).replace(/\{(\w+)\}/g, (hole, name) =>
    String(values[name] ?? platform[name] ?? hole),
  );

export const current = () => chosenLanguage.code;

/** @param {string} next */
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
