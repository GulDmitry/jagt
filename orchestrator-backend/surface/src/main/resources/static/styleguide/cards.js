// Every state a card is drawn in, by what a human must read off it.

const MINUTE = 60000;
const now = Date.now();
const verb = (id, label, group, extra) => ({id, label, hint: label, group, ...extra});
const tools = [verb('ide', 'Open IDE', 'tool'), verb('diff', 'Diff', 'tool'), verb('focus', 'Focus', 'tool', {onRow: true}),
  verb('respawn', 'Restart agent', 'tool')];
const task = (fields) => ({project: 'alpha', repos: [{project: 'alpha'}], pipeline: 'NONE', attention: 'NONE',
  owner: 'NONE', statusSince: now - 12 * MINUTE, history: [{at: now - 90 * MINUTE, status: 'NEW'},
    {at: now - 80 * MINUTE, status: 'IN_PROGRESS'}], actions: [verb('done', 'Done', 'flow'), ...tools], ...fields});
const card = (name, fields, extra) => ({name, props: {task: task(fields), manyProjects: false, flagged: false,
  blocked: new Set(), offers: [], ...extra}});

export const cards = [
  card('your move: a question', {id: 'ABC-42', alias: 'a1', title: 'Refund webhook retries twice on timeout',
    owner: 'YOU', attention: 'REQUIRED', ask: 'NEEDS INPUT', hint: 'the agent asked a question', status: 'IN_PROGRESS',
    statusLabel: 'agent working', detail: 'NEEDS INPUT: which retry budget, 3 or 5?', detailKind: 'YOURS',
    ticketUrl: 'https://tracker.example/ABC-42',
    actions: [verb('done', 'Done', 'flow'), verb('focus', 'Focus', 'tool', {primary: true, onRow: true}), verb('ide', 'Open IDE', 'tool')]}),
  card('agent working', {id: 'ABC-51', alias: 'a2', title: 'Invoice PDF misses the VAT line', owner: 'AGENT',
    status: 'IN_PROGRESS', statusLabel: 'agent working', actions: [verb('ship', 'Ship', 'flow'), verb('done', 'Done', 'flow'), ...tools]}),
  card('with the reviewers, checks running', {id: 'ABC-57', alias: 'a3', title: 'Currency rounding in the monthly report',
    owner: 'CI', status: 'CI_POLLING', statusLabel: 'out for review', pipeline: 'RUNNING',
    reviewRequestUrl: 'https://code.example/mr/1', requestOpenedAt: now - 120 * MINUTE, approved: false,
    autoReview: {state: 'WATCHING', note: 'watching', nextPollAt: now + 3 * MINUTE},
    actions: [verb('sweep', 'Check review', 'flow'), verb('done', 'Done', 'flow'), ...tools]}),
  card('broken: checks failed', {id: 'ABC-70', alias: 'a4', title: 'Dark mode for the settings page', owner: 'YOU',
    attention: 'REQUIRED', ask: 'NEEDS YOU', hint: 'checks failed', status: 'CI_FAILED', statusLabel: 'checks failed',
    pipeline: 'RED', reviewRequestUrl: 'https://code.example/mr/3', requestOpenedAt: now - 180 * MINUTE, approved: false,
    detail: 'PROBLEM: 2 tests fail in ThemeToggleTest', detailKind: 'PROBLEM',
    actions: [verb('sweep', 'Check review', 'flow', {primary: true}), verb('done', 'Done', 'flow'), ...tools]}),
  card('yours whenever you like: approved', {id: 'ABC-63', alias: 'a5', title: 'Login form loses focus after an error',
    owner: 'YOU', attention: 'OPTIONAL', ask: 'deploy', hint: 'approved; deploy when you like', status: 'APPROVED',
    statusLabel: 'approved', pipeline: 'GREEN', reviewRequestUrl: 'https://code.example/mr/2',
    requestOpenedAt: now - 1440 * MINUTE, approved: true,
    actions: [verb('deploy', 'Deploy', 'flow', {primary: true}), verb('done', 'Done', 'flow'), ...tools]}),
  card('live on a shared branch', {id: 'ABC-74', alias: 'a6', title: 'Footer links point at the old docs',
    status: 'DEPLOYED', statusLabel: 'deployed', pipeline: 'GREEN', approved: true, deployed: true,
    actions: [verb('deploy', 'Deploy', 'flow', {again: true}), verb('revert', 'Revert', 'flow'),
      verb('done', 'Done', 'flow', {primary: true}), ...tools]}),
  card('across repositories, approved', {id: 'ABC-80', alias: 'a7', title: 'Rename the tenant header', owner: 'CI',
    status: 'REVIEWED', statusLabel: 'not approved', approved: true, pipeline: 'GREEN',
    repos: [{project: 'alpha', reviewRequestUrl: 'https://code.example/mr/4'},
      {project: 'beta', reviewRequestUrl: 'https://code.example/mr/5'}]}),
  card('stopped: nothing polls again', {id: 'ABC-81', alias: 'a8', title: 'Cache headers on static files', owner: 'CI',
    status: 'CI_POLLING', statusLabel: 'out for review', reviewRequestUrl: 'https://code.example/mr/6',
    requestOpenedAt: now - 9 * 1440 * MINUTE, autoReview: {state: 'STALLED', label: 'poll stopped', note: 'request unreadable'}}),
  card('a pasted paragraph', {id: 'own-1', alias: 'a9', title: 'Spike: '
    + 'warm the cache on start, measure the first request, compare with a lazy load, and write down the numbers '
    + 'for each of the three environments before anything is changed in the configuration', owner: 'AGENT',
    status: 'IN_PROGRESS', statusLabel: 'agent working'}),
  card('a press in flight', {id: 'ABC-90', alias: 'b1', title: 'Ship the retry budget', owner: 'YOU',
    attention: 'REQUIRED', ask: 'ship it', hint: 'ship the round', status: 'REVIEW_PENDING', statusLabel: 'not shipped',
    actions: [verb('ship', 'Ship', 'flow', {primary: true}), verb('done', 'Done', 'flow'), ...tools]},
  {blocked: new Set(['ship'])}),
  card('flagged, a report offered', {id: 'ABC-91', alias: 'b2', title: 'Answer the reviewer on naming', owner: 'YOU',
    attention: 'OPTIONAL', ask: 'read the round', hint: 'replies drafted', status: 'REVIEW_PENDING',
    statusLabel: 'not shipped'}, {flagged: true, offers: [{id: 'replies', hint: 'the answers a round drafted'}]}),
  card('several projects in the install', {id: 'ABC-92', alias: 'b3', title: 'Bump the base image', project: 'beta',
    repos: [{project: 'beta'}], owner: 'AGENT', status: 'VERIFYING', statusLabel: 'verifying'}, {manyProjects: true}),
];
