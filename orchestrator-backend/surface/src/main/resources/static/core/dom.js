// Built, never interpolated into markup: ids, aliases and project keys come out of a file a human edits by hand.

export const span = (className, text) => {
  const node = document.createElement('span');
  if (className) node.className = className;
  node.textContent = text;
  return node;
};

// A mark whose hover is its only words: named for a screen reader, and focusable so the hover opens by keyboard.
export const named = (node, label) => {
  node.setAttribute('role', 'img');
  node.setAttribute('aria-label', label);
  node.tabIndex = 0;
  return node;
};

// Only http(s) becomes an href: a `javascript:` one would run in the page that can deploy.
export const link = (href, text) => {
  const anchor = document.createElement('a');
  if (/^https?:/i.test(href)) anchor.href = href;
  anchor.target = '_blank';
  anchor.rel = 'noreferrer';
  anchor.textContent = text;
  return anchor;
};
