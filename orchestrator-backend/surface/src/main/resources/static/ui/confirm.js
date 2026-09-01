// What a click is asked to confirm: only the clicks that cannot be taken back get one.

// One line per repository, since a task can move several and each has its own target.
const deployQuestion = (task) => {
  const lands = (task.repos || []).map((repo) =>
    `${repo.project} → ${repo.deployBranch || 'no deployBranch in jagt.yml'}`);
  return `Deploy ${task.id}?\n\nThis merges and pushes:\n${lands.join('\n')}`;
};

const revertQuestion = (task) => {
  const branches = (task.repos || []).map((repo) =>
    `${repo.project} → ${repo.deployBranch || 'no deployBranch in jagt.yml'}`);
  return `Revert ${task.id}?\n\nThis pushes a revert commit to:\n${branches.join('\n')}\n\n`
    + 'Only the LAST deploy comes out; earlier rounds stay live and need reverting by hand.'
    + ' The task branch keeps every commit.';
};

const QUESTIONS = {deploy: deployQuestion, revert: revertQuestion};

// `done` is asked because it destroys a worktree.
export const confirmation = (task, action) => {
  const ask = QUESTIONS[action.id];
  if (ask) return ask(task);
  return action.id === 'done' ? `${action.label} ${task.id}?\n\n${action.hint}` : null;
};
