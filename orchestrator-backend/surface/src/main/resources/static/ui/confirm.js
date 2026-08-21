// What a click is asked to confirm, in the words of what it is about to do. Only the clicks that cannot be taken
// back get one: a question on every button is a question nobody reads.

// A deploy is the one click that writes a branch other people build on, so the question names the exact writes
// it is asking for — one line per repository, since a task can move several and each has its own target.
const deployQuestion = (task) => {
  const lands = (task.repos || []).map((repo) =>
    `${repo.project} → ${repo.deployBranch || 'no deployBranch in config.json'}`);
  // A deploy lands what was SHIPPED, and only `ship` moves what a round left behind — so the question names what
  // would be left out rather than letting a click quietly deploy the previous round and mark the task DEPLOYED.
  // TWO facts, never one sentence: a round that reported `no changes` edited nothing, so there is nothing
  // unshipped to warn about, while its drafted answers may still be sitting in the worktree unposted.
  const unshipped = task.status === 'REVIEW_PENDING' && task.round !== 'NO_CHANGES'
    ? '\n\nCareful: the agent\u2019s latest changes were never shipped — a deploy lands the last SHIP, not the'
      + ' worktree. Ship first to include them.'
    : '';
  const drafts = task.draftedReplies
    ? '\n\nreview_replies.md is still in the worktree: if those answers were never posted, ship posts them.'
    : '';
  return `Deploy ${task.id}?\n\nThis merges and pushes:\n${lands.join('\n')}${unshipped}${drafts}`;
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
