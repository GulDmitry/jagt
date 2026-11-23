const tip = Object.assign(document.createElement("div"), {
  id: "tip",
  hidden: true,
});
document.body.append(tip);

/** @type {{ shownFor: Element | null }} */
const state = {
  shownFor: null,
};

/** @param {EventTarget | null} node */
const clipped = (node) => {
  for (let at = node; at instanceof Element; at = at.parentElement) {
    if (getComputedStyle(at).textOverflow === "ellipsis")
      return at.scrollWidth > at.clientWidth ? at : null;
  }
  return null;
};

/** @param {Element} node */
const said = (node) =>
  node.getAttribute("data-tip") ||
  (clipped(node) === node ? node.textContent : "");

export function hideTip() {
  tip.hidden = true;
}

/** @param {Element | null | undefined} [rebuilt] */
export function hideStaleTip(rebuilt) {
  if (!state.shownFor?.isConnected && rebuilt) state.shownFor = rebuilt;
  if (!state.shownFor?.isConnected || !said(state.shownFor)) hideTip();
  else if (!tip.hidden && said(state.shownFor) !== tip.textContent)
    showTip(state.shownFor);
}

/** @param {Element} target */
function showTip(target) {
  (target.closest("dialog[open]") || document.body).append(tip);
  state.shownFor = target;
  tip.textContent = said(target);
  tip.hidden = false;
  tip.scrollTop = 0;
  const scrolls = tip.scrollHeight > tip.clientHeight;
  tip.classList.toggle("scrolls", scrolls);
  const gap = scrolls ? 0 : 8;
  const anchor = target.getBoundingClientRect();
  const own = tip.getBoundingClientRect();
  const below = anchor.bottom + gap;
  const fits = below + own.height < window.innerHeight - 8;
  tip.style.left = `${Math.max(8, Math.min(anchor.left, window.innerWidth - own.width - 8))}px`;
  tip.style.top = `${fits ? below : Math.max(8, anchor.top - own.height - gap)}px`;
}

for (const event of ["pointerover", "focusin"]) {
  document.addEventListener(event, (moved) => {
    const from = moved.target instanceof Element ? moved.target : null;
    if (from?.closest("#tip")) return;
    const target = from?.closest("[data-tip]") || clipped(moved.target);
    if (target && said(target)) showTip(target);
    else hideTip();
  });
}
document.addEventListener("pointerdown", hideTip);
window.addEventListener(
  "scroll",
  (scrolled) => {
    if (scrolled.target !== tip) hideTip();
  },
  true,
);
