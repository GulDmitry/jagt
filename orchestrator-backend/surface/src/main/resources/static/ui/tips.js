// `title` shows only after a wait, and a push rebuilds the element it was waiting on — so a tip is delegated.

const tip = Object.assign(document.createElement('div'), {id: 'tip', hidden: true});
document.body.append(tip);

export function hideTip() {
  tip.hidden = true;
}

function showTip(target) {
  tip.textContent = target.dataset.tip;
  tip.hidden = false;
  const anchor = target.getBoundingClientRect();
  const own = tip.getBoundingClientRect();
  const below = anchor.bottom + 8;
  const fits = below + own.height < window.innerHeight - 8;
  tip.style.left = `${Math.max(8, Math.min(anchor.left, window.innerWidth - own.width - 8))}px`;
  tip.style.top = `${fits ? below : Math.max(8, anchor.top - own.height - 8)}px`;
}

for (const event of ['pointerover', 'focusin']) {
  document.addEventListener(event, (moved) => {
    const target = moved.target.closest?.('[data-tip]');
    if (target && target.dataset.tip) showTip(target);
    else hideTip();
  });
}
document.addEventListener('pointerdown', hideTip);
window.addEventListener('scroll', hideTip, true);
