// What NARROWS the board, and the controls that say so. Narrowing is offered instead of sorting: the server's
// order is the alias, and a position a human has learnt is worth more than any ordering a click could produce —
// while a filter is an explicit act with a visible control.

export const box = document.getElementById('filter');
const onlyMine = document.getElementById('mine');

// Every phase clicked stays selected until it is clicked again: narrowing to two of them is a real question
// ("what is not finished yet"), and a single-choice control cannot ask it.
const phases = new Set();

const matches = (task, needle) => [task.alias, task.id, task.title]
  .some((field) => (field || '').toLowerCase().includes(needle));

// Deliberately in two steps. The phase counts are the set narrowed by everything EXCEPT the phase choice: a
// count that obeyed it would read `build 0` the moment `review` was picked, and nothing could be clicked back.
export const narrowed = (tasks) => {
  const needle = box.value.trim().toLowerCase();
  return tasks.filter((task) => (!onlyMine.checked || task.attention === 'REQUIRED')
    && (!needle || matches(task, needle)));
};

export const shown = (tasks) => narrowed(tasks)
  .filter((task) => phases.size === 0 || phases.has(task.phase));

export const on = () => (box.value.trim() ? 1 : 0) + (onlyMine.checked ? 1 : 0) + phases.size;

export const holds = (phase) => phases.has(phase);

export const togglePhase = (phase) => {
  if (!phases.delete(phase)) phases.add(phase);
};

export function clear() {
  box.value = '';
  onlyMine.checked = false;
  phases.clear();
}

// The callback is the repaint, which this module must not reach for itself: what narrowing MEANS on screen is
// the render's answer, not the control's.
export const onChange = (repaint) => {
  box.oninput = repaint;
  onlyMine.onchange = repaint;
};
