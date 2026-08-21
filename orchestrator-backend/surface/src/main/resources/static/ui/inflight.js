// What is in flight, and it is TWO questions because they refuse different clicks. `pending` is this exact
// button (`<task>:<action>`), so a slow launch cannot be fired twice; `writing` is the task whose state
// something is changing, and every OTHER writing button on that card is refused while it is — two ships on one
// worktree race. A read-only action is in neither set's way: `sweep` runs for minutes, and being unable to look
// at the session meanwhile is exactly when a human wants to. WHICH actions write is the server's answer
// (`action.readOnly`), never a list of ids kept here — and `writing` remembers WHICH move, because a refusal
// that cannot name what it is waiting for reads as a dead button.

const pending = new Set();
const writing = new Map();

const key = (task, action) => `${task.id}:${action.id}`;

// What DISABLES a button is also what refuses the click: the palette reaches `run` with no button in between,
// or a typed line would start exactly what the card forbids.
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
