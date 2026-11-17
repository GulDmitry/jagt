// Built, never interpolated into markup: ids, aliases and project keys come out of a file a human edits by hand.

/**
 * @param {string} tag
 * @param {string} [className]
 * @param {string} [text]
 * @param {...(Node | string)} children
 */
export const make = (tag, className, text = "", ...children) => {
  const node = document.createElement(tag);
  if (className) node.className = className;
  node.textContent = text;
  node.append(...children);
  return node;
};

/**
 * @param {string | null} [className]
 * @param {string} [text]
 */
export const span = (className, text = "") => {
  const node = document.createElement("span");
  if (className) node.className = className;
  node.textContent = text;
  return node;
};

// A mark whose hover is its only words: named for a screen reader, and focusable so the hover opens by keyboard.
/**
 * @template {HTMLElement | SVGElement} T
 * @param {T} node
 * @param {string} label
 * @returns {T}
 */
export const named = (node, label) => {
  node.setAttribute("role", "img");
  node.setAttribute("aria-label", label);
  node.tabIndex = 0;
  return node;
};

// Only http(s) becomes an href: a `javascript:` one would run in the page that can deploy.
/** @param {string | null | undefined} href */
export const httpOnly = (href) =>
  /^https?:/i.test(href || "") ? href : undefined;

/**
 * @param {string | null | undefined} href
 * @param {string} text
 */
export const link = (href, text) => {
  const anchor = document.createElement("a");
  const safe = httpOnly(href);
  if (safe) anchor.href = safe;
  anchor.target = "_blank";
  anchor.rel = "noreferrer";
  anchor.textContent = text;
  return anchor;
};
