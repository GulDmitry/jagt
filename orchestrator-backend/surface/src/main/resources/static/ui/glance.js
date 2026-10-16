// What a human sees with the board behind another window: the tab's title counts their moves, its icon wears a dot.

const icon = document.querySelector('link[rel=icon][type="image/svg+xml"]');
const source = icon?.getAttribute('href');
let drawn = null;
let svg = null;

const token = (name) => getComputedStyle(document.documentElement).getPropertyValue(name).trim();

async function dot(colour) {
  if (!icon) return;
  if (!colour) {
    icon.href = source;
    return;
  }
  svg ??= await fetch(source).then((response) => response.text()).catch(() => null);
  if (!svg) return;
  const marked = svg.replace('</svg>',
    `<circle cx="25" cy="7" r="6.5" fill="${colour}" stroke="${token('--panel')}" stroke-width="2"/></svg>`);
  icon.href = `data:image/svg+xml,${encodeURIComponent(marked)}`;
}

// Broken outranks waiting: a red dot is what stopped, an amber one is what only waits on the human.
export function render({waiting, broken}) {
  document.title = waiting ? `(${waiting}) jagt` : 'jagt';
  const colour = broken ? token('--danger') : waiting ? token('--you') : '';
  if (colour === drawn) return;
  drawn = colour;
  dot(colour);
}
