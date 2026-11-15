// TWO questions, because they refuse different clicks: this exact button, and any other WRITING one on the task.

const pending = new Set();
const writing = new Map();

const key = (task, action) => `${task.id}:${action.id}`;

// What DISABLES a button also refuses the click: a typed line reaches `run` with no button in between.
export const blocked = (task, action) => pending.has(key(task, action))
  || (!action.readOnly && writing.has(task.id));

export const waitingFor = (task, action) => writing.get(task.id) || action.id;

export function hold(task, action) {
  pending.add(key(task, action));
  if (!action.readOnly) writing.set(task.id, action.id);
}

export function release(task, action) {
  pending.delete(key(task, action));
  if (!action.readOnly) writing.delete(task.id);
}
