// What NARROWS the board: a position a human has learnt is worth more than any order a click could produce.

export const box = document.getElementById('filter');
const onlyMine = document.getElementById('mine');

// Every phase clicked stays selected until clicked again: "what is not finished yet" is two of them, and a
// single-choice control cannot ask it.
const phases = new Set();

const matches = (task, needle) => [task.alias, task.id, task.title]
  .some((field) => (field || '').toLowerCase().includes(needle));

// The counts read the set narrowed by everything EXCEPT the phase choice: one that obeyed it would read
// `build 0` the moment `review` was picked, and nothing could be clicked back.
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

export const onChange = (repaint) => {
  box.oninput = repaint;
  onlyMine.onchange = repaint;
};
