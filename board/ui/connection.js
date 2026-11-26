const LOCAL = "#filter, #gear, #gear-dialog section[id], [data-t=close]";

/** @param {Element} node @param {boolean} yes */
const settle = (node, yes) => {
  if (node.closest(LOCAL)) return;
  if (node.querySelector(LOCAL))
    [...node.children].forEach((child) => settle(child, yes));
  else /** @type {HTMLElement} */ (node).inert = !yes;
};

/** @param {boolean} yes */
export const connected = (yes) => {
  /** @type {HTMLElement} */ (document.getElementById("offline")).hidden = yes;
  document.body.classList.toggle("stale", !yes);
  settle(document.body, yes);
};
