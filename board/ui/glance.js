const found = document.querySelector('link[rel=icon][type="image/svg+xml"]');
const icon = found instanceof HTMLLinkElement ? found : null;
const source = icon?.getAttribute("href");
/** @type {{ drawn: string | null, svg: string | null }} */
const state = {
  drawn: null,
  svg: null,
};

/** @param {string} name */
const token = (name) =>
  getComputedStyle(document.documentElement).getPropertyValue(name).trim();

/** @param {string} colour */
async function dot(colour) {
  if (!icon || !source) return;
  if (!colour) {
    icon.href = source;
    return;
  }
  state.svg ??= await fetch(source)
    .then((response) => response.text())
    .catch(() => null);
  // A later paint may have changed the colour while the icon was being read.
  if (!state.svg || colour !== state.drawn) return;
  const marked = state.svg.replace(
    "</svg>",
    `<circle cx="25" cy="7" r="6.5" fill="${colour}" stroke="${token("--panel")}" stroke-width="2"/></svg>`,
  );
  icon.href = `data:image/svg+xml,${encodeURIComponent(marked)}`;
}

// Broken outranks waiting: a red dot is what stopped, an amber one is what only waits on the human.
/** @param {{ waiting: number, broken: boolean }} counts */
export function render({ waiting, broken }) {
  document.title = waiting ? `(${waiting}) jagt` : "jagt";
  const colour = broken ? token("--danger") : waiting ? token("--you") : "";
  if (colour === state.drawn) return;
  state.drawn = colour;
  dot(colour);
}
