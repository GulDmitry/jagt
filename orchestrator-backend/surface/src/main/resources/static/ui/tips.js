// `title` shows only after a wait, and a push rebuilds the element it was waiting on — so a tip is delegated.

const tip = Object.assign(document.createElement('div'), {id: 'tip', hidden: true});
document.body.append(tip);

export function hideTip() {
  tip.hidden = true;
}

function showTip(target) {
  // A modal dialog renders in the top layer, above every z-index: a tip under it hides behind the backdrop.
  (target.closest('dialog[open]') || document.body).append(tip);
  tip.textContent = target.dataset.tip;
  tip.hidden = false;
  tip.scrollTop = 0;
  // Past its cap a tip is READ, not glanced at: it takes the pointer to scroll, and leaves no gap to cross.
  const scrolls = tip.scrollHeight > tip.clientHeight;
  tip.classList.toggle('scrolls', scrolls);
  const gap = scrolls ? 0 : 8;
  const anchor = target.getBoundingClientRect();
  const own = tip.getBoundingClientRect();
  const below = anchor.bottom + gap;
  const fits = below + own.height < window.innerHeight - 8;
  tip.style.left = `${Math.max(8, Math.min(anchor.left, window.innerWidth - own.width - 8))}px`;
  tip.style.top = `${fits ? below : Math.max(8, anchor.top - own.height - gap)}px`;
}

for (const event of ['pointerover', 'focusin']) {
  document.addEventListener(event, (moved) => {
    if (moved.target.closest?.('#tip')) return;
    const target = moved.target.closest?.('[data-tip]');
    if (target && target.dataset.tip) showTip(target);
    else hideTip();
  });
}
document.addEventListener('pointerdown', hideTip);
window.addEventListener('scroll', (scrolled) => {
  if (scrolled.target !== tip) hideTip();
}, true);
