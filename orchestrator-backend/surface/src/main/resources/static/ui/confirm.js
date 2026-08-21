// What a click is asked to confirm, in the words of what it is about to do. Only the clicks that cannot be taken
// back get one: a question on every button is a question nobody reads.

// A deploy is the one click that writes a branch other people build on, so the question names the exact writes
// it is asking for — one line per repository, since a task can move several and each has its own target. It
// advises nothing about whether the work is worth shipping first: that reading was wrong often enough to be
// clicked past, and a dialog nobody reads takes the rest of itself with it.
const deployQuestion = (task) => {
  const lands = (task.repos || []).map((repo) =>
    `${repo.project} → ${repo.deployBranch || 'no deployBranch in config.json'}`);
  return `Deploy ${task.id}?\n\nThis merges and pushes:\n${lands.join('\n')}`;
};

const revertQuestion = (task) => {
  const branches = (task.repos || []).map((repo) =>
    `${repo.project} → ${repo.deployBranch || 'no deployBranch in config.json'}`);
  return `Revert ${task.id}?\n\nThis pushes a revert commit to:\n${branches.join('\n')}\n\n`
    + 'Only the LAST deploy of this task comes out — if it was deployed more than once, the earlier rounds stay'
    + ' live and have to be reverted by hand. The task branch keeps every commit.';
};

const QUESTIONS = {deploy: deployQuestion, revert: revertQuestion};

// Null when the click needs no confirmation. `done` destroys a worktree; the shared-branch writes name what
// they push.
export const confirmation = (task, action) => {
  const ask = QUESTIONS[action.id];
  if (ask) return ask(task);
  return action.id === 'done' ? `${action.label} ${task.id}?\n\n${action.hint}` : null;
};
