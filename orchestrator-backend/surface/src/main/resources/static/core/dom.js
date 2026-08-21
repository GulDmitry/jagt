// Every piece of a card is BUILT, never interpolated into markup: ids, aliases and project keys come out of a
// state file the human is invited to edit by hand, and an assumption about their shape is invisible from here.

export const span = (className, text) => {
  const node = document.createElement('span');
  if (className) node.className = className;
  node.textContent = text;
  return node;
};

export const link = (href, text) => {
  const anchor = document.createElement('a');
  anchor.href = href;
  anchor.target = '_blank';
  anchor.rel = 'noreferrer';
  anchor.textContent = text;
  return anchor;
};
