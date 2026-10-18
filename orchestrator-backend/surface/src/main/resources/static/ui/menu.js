// The Reports menu: one button opens the list, and a pick, a press elsewhere or Escape closes it.

const toggle = document.getElementById('reports-toggle');
const list = document.getElementById('reports');

export const close = () => {
  const wasOpen = !list.hidden;
  list.hidden = true;
  toggle.setAttribute('aria-expanded', 'false');
  return wasOpen;
};

const open = () => {
  list.hidden = false;
  toggle.setAttribute('aria-expanded', 'true');
  list.querySelector('button')?.focus();
};

toggle.onclick = () => (list.hidden ? open() : close());
list.addEventListener('click', (event) => {
  if (event.target.closest('button')) close();
});
document.addEventListener('pointerdown', (event) => {
  if (!list.hidden && !event.target.closest('.menu')) close();
});
