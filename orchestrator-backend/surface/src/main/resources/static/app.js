// Plain DOM on purpose: no build step, no CDN, works with the machine offline.
//
// It holds NO rules of its own: every card renders the server's projection (phase, owner, hint, legal
// actions), and a button posts the action id back. If the server did not list an action, there is no button —
// and if the page is stale, the POST is refused with a sentence, which is what the toast shows.

import {api, refusal, text} from './core/api.js';
import {link, span} from './core/dom.js';
import {countdown, duration} from './core/format.js';
import * as store from './core/store.js';
import * as filters from './ui/filters.js';

const PHASES = [
  ['BUILD', 'build'], ['REVIEW', 'review'], ['CHECK', 'check'],
  ['READY', 'ready'], ['DEPLOY', 'deploy'], ['DONE', 'done'],
];

const board = document.getElementById('board');
const phaseBar = document.getElementById('phases');
const toasts = document.getElementById('toasts');
const live = document.getElementById('live');
const projectSelect = document.getElementById('project');
let renderedProjects = null;
// An untouched default is not a decision, and naming a project SKIPS the ticket read — the escape hatch a
// typed `do ABC-1 <project>` deliberately takes. So the list opens on a real project, and only a pick is sent.
let projectPicked = false;
projectSelect.onchange = () => { projectPicked = true; };
// What is in flight, and it is TWO questions because they refuse different clicks. `pending` is this exact
// button (`<task>:<action>`), so a slow launch cannot be fired twice; `writing` is the task whose state
// something is changing, and every OTHER writing button on that card is refused while it is — two ships on one
// worktree race. A read-only action is in neither set's way: `sweep` runs for minutes, and being unable to look
// at the session meanwhile is exactly when a human wants to. WHICH actions write is the server's answer
// (`action.readOnly`), never a list of ids kept here — and `writing` remembers WHICH move, because a refusal
// that cannot name what it is waiting for reads as a dead button.
const pending = new Set();
const writing = new Map();

// What the unattended poller is about to do with this task, or nothing when it is not its business. The WORDS are
// the server's (watch.note), so the console and this cannot drift; only the countdown is formatted here, from the
// absolute stamp, so the slow repaint keeps it honest without another fetch.
//
// Whether the poller runs at all is a property of the install, stated once per surface, so neither shape repeats
// it: a poll that is COMING is its countdown, one that has STOPPED says what stopped. What to do instead is the
// tooltip, since the card already highlights that button.
const watchLine = (watch) => {
  if (!watch || !watch.note) return null;
  if (watch.state === 'WATCHING') {
    const remaining = watch.nextPollAt - Date.now();
    return {pulse: `next poll ${remaining <= 0 ? 'due' : countdown(remaining)}`, tip: watch.note};
  }
  return {pulse: watch.label, tip: watch.note, stalled: true};
};

// `openedAt` is 0 until a host read has said when the request opened.
const requestLink = (url, label, openedAt) => {
  const anchor = link(url, openedAt > 0 ? `${label} ${duration(Date.now() - openedAt)}` : label);
  anchor.className = 'mr-age';
  anchor.dataset.tip = openedAt > 0
    ? `review request; opened ${new Date(openedAt).toLocaleString()}`
    : 'review request; nothing has dated it yet — the next sweep will';
  return anchor;
};

// The transitions as a tooltip: the card stays one line, the record is one hover away.
const timeline = (task) => (task.history || [])
  .map((step) => {
    const asked = step.origin ? `  (${step.origin.toLowerCase().replace('_', '-')})` : '';
    return `${new Date(step.at).toLocaleString()}  ${step.status}${asked}`;
  })
  .join('\n');

// A toast is gone in seconds, so every one is also kept here for the session. No persistence: the backend's
// own log file keeps what matters longer.
const messages = [];

function toast(message, isError) {
  messages.push(`${new Date().toLocaleTimeString()}  ${message}`);
  const opener = document.getElementById('show-log');
  opener.hidden = false;
  opener.textContent = message.split('\n')[0];

  const node = document.createElement('div');
  node.className = isError ? 'toast error' : 'toast';
  node.textContent = message;
  node.onclick = () => node.remove();
  toasts.append(node);
  setTimeout(() => node.remove(), isError ? 12000 : 7000);
}

// Fetched, not hardcoded: the palette completes and validates against the SERVER's verb list, so a command the
// console accepts can never be missing from the suggestions here.
async function loadVerbs() {
  try {
    store.set({verbs: await api('/api/commands')});
  } catch (e) {
    store.set({verbs: []});          // no suggestions is a degraded palette, not a broken one
  }
  refreshSuggestions();
}

async function load() {
  try {
    const data = await api('/api/tasks');
    store.set({
      tasks: data.tasks,
      projects: data.projects || [],
      autoReview: {summary: data.autoReview, enabled: data.autoReviewEnabled},
      jobs: data.jobs,
    });
    fillProjects();
    render();
  } catch (e) {
    toast(`Cannot reach the backend: ${e.message}`, true);
  }
}

// The order they were picked in does not survive a multi-select, so the CONFIGURED order decides which
// repository the agent's session runs in.
function pickedProjects() {
  return [...projectSelect.selectedOptions].map((option) => option.value).join(',');
}

function fillProjects() {
  const configured = store.projects();
  const signature = configured.join('\n');
  if (renderedProjects === signature) {
    return;                       // a rebuild collapses the list under a human who has it open
  }
  renderedProjects = signature;
  const chosen = projectSelect.value;
  projectSelect.replaceChildren(...(configured.length
    ? configured.map((p) => new Option(p, p))
    : [Object.assign(new Option('no projects in config.json', ''), {disabled: true})]));
  if (configured.includes(chosen)) {
    projectSelect.value = chosen;
  } else {
    projectPicked = false;
  }
}

// `title` shows only after a wait, and a push rebuilds the element it was waiting on.
const tip = Object.assign(document.createElement('div'), {id: 'tip', hidden: true});
document.body.append(tip);

function hideTip() {
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

// Delegated: every element carrying a tip is rebuilt on render.
for (const event of ['pointerover', 'focusin']) {
  document.addEventListener(event, (moved) => {
    const target = moved.target.closest?.('[data-tip]');
    if (target && target.dataset.tip) showTip(target);
    else hideTip();
  });
}
document.addEventListener('pointerdown', hideTip);
window.addEventListener('scroll', hideTip, true);

// A failed run OUTRANKS the countdown: the next run is not news while the last one is still broken.
function renderJobs() {
  const chip = document.getElementById('jobs-pulse');
  chip.hidden = !store.jobs() || !store.jobs().count;
  if (chip.hidden) {
    return;
  }
  const failing = store.jobs().failing;
  // A run writes no state, so nothing pushes a fresh stamp here: this one is from the last state change and
  // goes into the past within the minute. Past means DUE, not `0s` — a countdown frozen at zero reads as broken.
  const due = store.jobs().nextRunAt ? store.jobs().nextRunAt - Date.now() : null;
  chip.textContent = failing
    ? `jobs: ${failing} failed`
    : `jobs: ${due === null ? 'next -' : due > 0 ? `next ${countdown(due)}` : 'due'}`;
  chip.classList.toggle('bad', failing > 0);
  chip.dataset.tip = failing
    ? 'a job\u2019s last run failed; open the Jobs report'
    : 'next scheduled run of any unattended job; the ticker runs every minute';
}

function render() {
  hideTip();
  const tasks = store.tasks();
  const shown = filters.shown(tasks);
  const waiting = tasks.filter((t) => t.owner === 'YOU').length;
  const waitingLabel = document.getElementById('waiting');
  waitingLabel.hidden = waiting === 0;
  waitingLabel.textContent = `${waiting} need your action`;
  const chip = document.getElementById('auto-review');
  chip.textContent = store.autoReview().summary || '';
  chip.classList.toggle('on', store.autoReview().enabled);
  renderJobs();
  renderEmpty(shown.length);
  // The pipeline is a COUNT, never a position: a phase that owns a column has to move the card it describes,
  // and re-finding it is the cost. Every phase is here, zeros included, so this line never moves either.
  phaseBar.hidden = tasks.length === 0;
  const perPhase = filters.narrowed(tasks);
  // The separator is CONTENT, not a gap: a line whose words only come apart when a stylesheet loads is one
  // stylesheet away from reading `build 0review 1`.
  phaseBar.replaceChildren(...PHASES.flatMap(([phase, label], index) => {
    const held = perPhase.filter((task) => task.phase === phase).length;
    const segment = document.createElement('button');
    segment.className = held ? 'phase' : 'phase empty';
    segment.append(`${label} `, span('count', held));
    // Nothing to show is nothing to press, and an empty phase says so rather than answering with a blank board.
    segment.disabled = held === 0;
    segment.setAttribute('aria-pressed', String(filters.holds(phase)));
    segment.dataset.tip = filters.holds(phase) ? `stop showing only ${label}` : `show only ${label}`;
    segment.onclick = () => {
      filters.togglePhase(phase);
      render();
    };
    return index === 0 ? [segment] : [span('sep', ' · '), segment];
  }));
  if (filters.on()) {
    const clear = document.createElement('button');
    clear.className = 'clear-filters';
    clear.textContent = `clear ${filters.on()} filter(s)`;
    clear.onclick = () => { filters.clear(); render(); };
    phaseBar.append(clear);
  }
  board.replaceChildren(...shown.map(card));
}

// An empty board has two causes and a human cannot act on the wrong one: nothing exists yet, or everything is
// hidden by controls they may not be looking at. Two elements rather than one message rewritten in place, so
// neither can be left showing the other's text.
function renderEmpty(showing) {
  const held = store.tasks().length;
  const filteredOut = held > 0 && showing === 0;
  document.getElementById('empty').hidden = held > 0;
  const filtered = document.getElementById('filtered');
  filtered.hidden = !filteredOut;
  if (filteredOut) {
    filtered.textContent = `No task matches: ${filters.on()} filter(s) on, ${held} task(s) hidden.`;
  }
}

function card(task) {
  const owner = task.owner.toLowerCase();
  const article = document.createElement('article');
  article.className = owner;

  const top = document.createElement('div');
  top.className = 'card-top';
  top.append(span('alias', task.alias || '-'),
    task.ticketUrl ? Object.assign(link(task.ticketUrl, task.id), {className: 'id'}) : span('id', task.id));
  // Only YOUR move is news; every other owner is the status word again. The case this keeps is an agent that
  // stopped, which flips the owner in a phase where nothing else says so.
  if (task.owner === 'YOU') {
    const badge = span('badge you', 'action required');
    badge.dataset.tip = task.hint;
    top.append(badge);
  }

  const title = document.createElement('div');
  title.className = 'title';
  title.textContent = task.title || '';

  const meta = document.createElement('div');
  meta.className = 'meta';
  // Two clocks, because the status one restarts on every round and on a respawned agent re-reporting itself
  // while the review keeps waiting. The activity age is not a third: on a status the agent does not own, it
  // says only that nothing has happened.
  // The age is INSIDE the status: a bare duration between two separators reads as a fact of its own.
  const status = span('status', task.statusLabel);
  status.append(' ', span('age', duration(Date.now() - task.statusSince)));
  status.dataset.tip = `${task.status}\n${timeline(task)}`;
  // One session, one or more repositories: naming them all is what tells you this task moves two codebases.
  const repos = task.repos || [];
  const where = repos.length > 1 ? repos.map((r) => r.project).join(' + ') : task.project;
  meta.append(status, span(null, where));
  // ONE stamp for several requests — the oldest — so it can be worn only where there is one request to wear
  // it. Several are named by project and ageless: the same number under each would read as each one's own.
  if (task.reviewRequestUrl && repos.length < 2) {
    meta.append(requestLink(task.reviewRequestUrl, 'MR', task.requestOpenedAt));
  } else {
    for (const repo of repos.filter((each) => each.reviewRequestUrl)) {
      meta.append(requestLink(repo.reviewRequestUrl, `${repo.project} MR`, 0));
    }
  }
  // The approval, as one dot beside the request: whether anyone has approved is what decides if this card is
  // waiting on a person or on the human reading it, and no status says so until the approval has landed.
  if (task.approved != null) {
    const approval = span(task.approved ? 'approval yes' : 'approval', '');
    approval.dataset.tip = task.approved ? 'review request approved' : 'review request not approved yet';
    meta.append(approval);
  }
  // The checks, as one dot: the sweep already reads the pipeline, and a red run while the card still says
  // CI_POLLING is the thing a status word cannot show.
  if (task.pipeline && task.pipeline !== 'NONE') {
    const checks = span(`checks ${task.pipeline.toLowerCase()}`, '');
    checks.dataset.tip = `checks: ${task.pipelineSaid || task.pipeline.toLowerCase()}`;
    meta.append(checks);
  }
  const watch = watchLine(task.autoReview);
  if (watch) {
    const pulse = span(watch.stalled ? 'pulse stalled' : 'pulse', watch.pulse);
    pulse.dataset.tip = watch.tip;
    meta.append(pulse);
  }

  const article_children = [top, title, meta];

  if (task.detail) {
    const detail = document.createElement('div');
    detail.className = /^(PROBLEM|NEEDS)/.test(task.detail) ? 'detail problem' : 'detail';
    detail.textContent = task.detail;
    article_children.push(detail);
  }

  // Nothing else on the page would say the drafted answers exist, and `ship` is what posts them. The line that
  // announces them is also what OPENS them: a human approving a round reads it here, not in an editor.
  if (task.draftedReplies) {
    const drafts = document.createElement('button');
    drafts.className = 'drafts';
    drafts.textContent = 'review replies drafted, not posted \u2014 read them; ship posts them';
    drafts.dataset.tip = 'every comment and the reply that will be sent for it';
    const reference = task.alias || task.id;
    drafts.onclick = () => openReport(`replies ${reference}`,
      `/api/commands/replies?about=${encodeURIComponent(reference)}`);
    article_children.push(drafts);
  }

  // A row per group, broken wherever the order the server sent changes it: which groups exist, and which
  // comes first, stays the projection's answer.
  let row = null;
  for (const action of task.actions) {
    if (!row || row.dataset.group !== action.group) {
      row = actionRow(action.group);
      article_children.push(row);
    }
    const button = document.createElement('button');
    button.textContent = action.label;
    button.dataset.tip = action.hint;
    if (action.primary) button.className = 'primary';
    button.disabled = blocked(task, action);
    button.onclick = () => run(task, action);
    row.append(button);
  }
  article.append(...article_children);
  return article;
}

const inFlightKey = (task, action) => `${task.id}:${action.id}`;

// The palette reaches `run` with no button in between, so what DISABLES a button is also what refuses the click:
// one expression, or a typed line starts exactly what the card forbids.
const blocked = (task, action) => pending.has(inFlightKey(task, action))
  || (!action.readOnly && writing.has(task.id));

function actionRow(group) {
  const row = document.createElement('div');
  row.className = `actions ${group}`;
  row.dataset.group = group;
  return row;
}

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

async function run(task, action) {
  if (blocked(task, action)) {
    toast(`${task.id} is already running ${writing.get(task.id) || action.id} — wait for that to finish`, true);
    return;
  }
  // `done` destroys a worktree; the shared-branch writes name what they push.
  const ask = QUESTIONS[action.id];
  const question = ask ? ask(task) : `${action.label} ${task.id}?\n\n${action.hint}`;
  if ((ask || action.id === 'done') && !confirm(question)) {
    return;
  }
  const key = inFlightKey(task, action);
  pending.add(key);
  if (!action.readOnly) writing.set(task.id, action.id);
  render();
  try {
    const result = await api(`/api/tasks/${encodeURIComponent(task.id)}/actions/${action.id}`, {method: 'POST'});
    toast(result.message);
    if (action.id === 'focus') await openTerminal(task);
  } catch (e) {
    toast(refusal(e), true);
  } finally {
    pending.delete(key);
    if (!action.readOnly) writing.delete(task.id);
    await load();
  }
}

const launchForm = document.getElementById('launch');

function toggleForm(form, button, onOpen) {
  form.hidden = !form.hidden;
  button.setAttribute('aria-pressed', String(!form.hidden));
  if (!form.hidden && onOpen) onOpen();
}

launchForm.onsubmit = async (event) => {
  event.preventDefault();
  const state = document.getElementById('launch-state');
  const button = launchForm.querySelector('button[type=submit]');
  button.disabled = true;
  state.textContent = 'reading the ticket…';        // a model call: seconds, sometimes tens of them
  try {
    const result = await api('/api/tasks', {
      method: 'POST',
      headers: {'Content-Type': 'application/json'},
      body: JSON.stringify({
        ref: document.getElementById('ref').value,
        project: projectPicked ? pickedProjects() : '',
        mode: document.getElementById('plan').checked ? 'plan' : null,
        baseBranch: document.getElementById('base-branch').value,
        notes: document.getElementById('notes').value,
      }),
    });
    toast(result.message);
    launchForm.reset();
    projectPicked = false;
  } catch (e) {
    toast(e.message, true);
  } finally {
    button.disabled = false;
    state.textContent = '';
    await load();
  }
};

filters.onChange(render);

// A desktop notification about one task links here with `?task=<id>`, and it lands in the FILTER rather than in
// a selection of its own: the card then stands alone with its actions, the control that did it is visible, and
// clearing it is the button already on the page. An id nothing matches shows the "no task matches" line, which
// is the truth — the task was closed while the banner sat there.
const deepLink = new URLSearchParams(window.location.search).get('task');
if (deepLink) {
  filters.box.value = deepLink;
}

// Push, not poll: the backend tells us when state changed. The slow interval only refreshes the relative
// clocks ("4m ago"), which no event can announce.
// A reconnect is not a resync: the events missed while the backend was down are gone.
const events = new EventSource('/api/events');
events.addEventListener('open', () => {
  live.classList.add('on');
  loadVerbs();
  load();
});
events.addEventListener('changed', load);
events.onerror = () => live.classList.remove('on');
setInterval(render, 15000);
loadVerbs();
load();

// Tier 2 of the dispatch: free text, mapped to ONE command by a model and executed by the same gate the
// buttons use. Kept behind ⌘K rather than in the way, because tier 1 (a button, a typed command) costs
// nothing and this costs a model call — the point is flexibility when it is wanted, not by default.
const palette = document.getElementById('palette');
const ask = document.getElementById('ask');

function togglePalette(show) {
  palette.hidden = !show;
  document.getElementById('open-palette').setAttribute('aria-pressed', String(show));
  if (show) {
    ask.focus();
    ask.select();
  }
}

document.getElementById('open-palette').onclick = () => { togglePalette(palette.hidden); judgeAsk(); };
const typingInto = (target) => target instanceof HTMLInputElement || target instanceof HTMLTextAreaElement
  || target instanceof HTMLSelectElement;

document.addEventListener('keydown', (event) => {
  if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 'k') {
    event.preventDefault();
    togglePalette(palette.hidden);
  } else if (event.key === 'Escape' && !palette.hidden) {
    togglePalette(false);
  } else if (event.key === '/' && !typingInto(event.target)) {
    event.preventDefault();
    filters.box.focus();
  } else if (event.key === 'Escape' && event.target === filters.box) {
    filters.box.value = '';
    render();
  }
});

// A verb answers to its own name and to whatever it was renamed from: accepted here as the console accepts it,
// and offered in neither.
function verbFor(word) {
  const typed = word.toLowerCase();
  // Its own name first, exactly as the server resolves it: an alias must never shadow another verb's id.
  return store.verbs().find((v) => v.id === typed)
    || store.verbs().find((v) => (v.aliases || []).includes(typed));
}

// Understood WITHOUT a model: a known verb, and — for the per-task ones — a task that actually exists. Anything
// else is left to tier 2.
function parseCommand(line) {
  const tokens = line.trim().split(/\s+/).filter(Boolean);
  if (!tokens.length) return null;
  const verb = verbFor(tokens[0]);
  if (!verb) return null;
  const argument = tokens.slice(1).join(' ');
  if (!verb.takesTask) return {verb, argument};
  return {verb, argument, task: store.taskFor(argument)};
}

function refreshSuggestions() {
  document.getElementById('ask-options').replaceChildren(
    ...store.verbs().map((verb) => Object.assign(document.createElement('option'),
      {value: verb.id, label: verb.hint})));
  // One button per report the SERVER declares, so a new one appears in the toolbar without this page learning
  // its name.
  document.getElementById('reports').replaceChildren(
    ...store.verbs().filter((verb) => verb.report).map((verb) => {
      const button = document.createElement('button');
      button.id = `show-${verb.id}`;
      button.textContent = verb.id.charAt(0).toUpperCase() + verb.id.slice(1);
      button.dataset.tip = verb.hint;
      button.onclick = () => openReport(`${verb.id} — ${verb.hint}`, `/api/commands/${verb.id}`);
      return button;
    }));
  const resume = verbFor('resume');
  if (resume) {
    document.querySelectorAll('#resume-task, #resume button[type=submit]')
      .forEach((button) => { button.dataset.tip = resume.hint; });
  }
}

// The verdict, live: a typo must be visible before Run, not after a model has been paid to guess at it.
function judgeAsk() {
  const state = document.getElementById('palette-state');
  const line = ask.value.trim();
  state.classList.remove('ok', 'bad');
  if (!line) {
    state.textContent = '';
    return;
  }
  const parsed = parseCommand(line);
  if (!parsed) {
    const word = line.split(/\s+/)[0];
    const known = Boolean(verbFor(word));
    state.textContent = known ? '' : `“${word}” is not a command — this will go to the model as plain words`;
    return;
  }
  if (parsed.verb.takesTask && !parsed.task) {
    state.classList.add('bad');
    state.textContent = parsed.argument
      ? `no task “${parsed.argument}” — use a ticket id or its alias`
      : `${parsed.verb.id} needs a task: ${parsed.verb.id} <ticket|alias>`;
    return;
  }
  state.classList.add('ok');
  state.textContent = `runs as typed — ${parsed.verb.hint}`;
}

ask.addEventListener('input', judgeAsk);

// Tier 1 first: a line that parses is EXECUTED, not interpreted — deterministic, instant and free. Only real
// free text reaches /api/interpret.
async function runParsed(parsed) {
  const {verb, argument, task} = parsed;
  if (verb.takesTask) {
    // The card's own action carries what the projection said about this verb — whether it writes, above all, so
    // a typed line locks exactly what the button of the same name locks. A verb the card does not offer counts
    // as a write, which is the safe side: every read-only action is offered from every status.
    const offered = (task.actions || []).find((action) => action.id === verb.id);
    await run(task, offered || {id: verb.id, label: verb.id, hint: verb.hint});
    return `${verb.id} ${task.alias || task.id}`;
  }
  // Reports are named by the server rather than listed here, so one more needs no branch in this page. What was
  // typed after the verb goes with it: a report that narrows to one task must not silently answer for all of them.
  if (verb.report) {
    const about = argument ? `?about=${encodeURIComponent(argument)}` : '';
    showReport(`${verb.id} ${argument}`.trim(),
      await text(`/api/commands/${encodeURIComponent(verb.id)}${about}`));
    return `${verb.id} ${argument}`.trim();
  }
  if (verb.id === 'do') {
    if (!argument) { document.getElementById('ref').focus(); return 'do'; }
    const result = await api('/api/tasks', {
      method: 'POST',
      headers: {'Content-Type': 'application/json'},
      body: JSON.stringify({ref: argument}),
    });
    toast(result.message);
    return `do ${argument}`;
  }
  if (verb.id === 'resume') {
    if (!argument.startsWith('http')) {
      document.getElementById('resume-task').click();
      document.getElementById('resume-url').value = argument;
      return 'resume';
    }
    const result = await api('/api/tasks/resume', {
      method: 'POST',
      headers: {'Content-Type': 'application/json'},
      body: JSON.stringify({reviewRequestUrl: argument}),
    });
    toast(result.message);
    return `resume ${argument}`;
  }
  return null;
}

palette.onsubmit = async (event) => {
  event.preventDefault();
  const state = document.getElementById('palette-state');
  const button = palette.querySelector('button[type=submit]');
  const parsed = parseCommand(ask.value);
  // A verb typed ALONE is answered here: the model would be paid to map a line with no argument at all, and
  // would answer "unknown command" for a verb the page just completed. A verb WITH an argument that named no
  // task is prose ("ship the widget one") and stays tier 2's job.
  if (parsed && parsed.verb.takesTask && !parsed.task && !parsed.argument) {
    judgeAsk();
    return;
  }
  if (parsed && (!parsed.verb.takesTask || parsed.task)) {
    button.disabled = true;
    try {
      const ran = await runParsed(parsed);
      if (ran) { ask.value = ''; togglePalette(false); return; }
    } catch (e) {
      toast(refusal(e), true);
      return;
    } finally {
      button.disabled = false;
      judgeAsk();
      await load();
    }
  }
  button.disabled = true;
  state.textContent = 'interpreting…';           // a model call: seconds
  try {
    const result = await api('/api/interpret', {
      method: 'POST',
      headers: {'Content-Type': 'application/json'},
      body: JSON.stringify({text: ask.value}),
    });
    toast(result.message);
    ask.value = '';
    togglePalette(false);
  } catch (e) {
    toast(refusal(e), true);
  } finally {
    button.disabled = false;
    state.textContent = '';
    await load();
  }
};

const report = document.getElementById('report');
const reportTitle = document.getElementById('report-title');
const reportBody = document.getElementById('report-body');

// One dialog for every plain-text answer the backend gives: a native <dialog> dims the board itself, closes on
// Escape, and — unlike a new tab — cannot be lost behind the window you were already reading.
function showReport(title, text) {
  reportTitle.textContent = title;
  reportBody.textContent = text.trimEnd();
  if (!report.open) report.showModal();
  reportBody.scrollTop = 0;
}

document.getElementById('close-report').onclick = () => report.close();

// The dimmed area around a dialog is part of it, and a modal that answers only its own button reads as stuck.
// Which element the press STARTED on decides: selecting text inside and releasing outside is not a request to
// close what you were reading.
function closeOnBackdrop(dialog) {
  let pressedBackdrop = false;
  dialog.addEventListener('mousedown', (event) => { pressedBackdrop = event.target === dialog; });
  dialog.addEventListener('click', (event) => {
    if (pressedBackdrop && event.target === dialog) dialog.close();
  });
}

closeOnBackdrop(report);
document.getElementById('show-log').onclick = () => showReport('log — this session', messages.join('\n'));

// Focus, rendered rather than announced: the action selects the agent's tmux window whatever surface asked, and
// when a web terminal serves that session the board shows it right here. With none configured there is nothing
// to open and the sentence in the toast — which window the session is in — is the whole answer.
const terminalDialog = document.getElementById('terminal');
const terminalFrame = document.getElementById('terminal-frame');

async function openTerminal(task) {
  let port;
  try {
    ({port} = await api(`/api/tasks/${encodeURIComponent(task.id)}/terminal`, {method: 'POST'}));
  } catch (e) {
    toast(refusal(e), true);      // no port is silence; a refusal is not, or a gone task reads as "not set up"
    return;
  }
  if (!port) return;
  // The server answers with a port only: the host is whatever name this page reached jagt under, and the
  // terminal runs on the same machine — an address chosen there would be jagt's own loopback, not ours.
  const url = `http://${location.hostname}:${port}`;
  document.getElementById('terminal-title').textContent = `${task.alias || task.id} · ${task.id}`;
  // Re-pointing the frame at the same address attaches a second client for nothing.
  if (terminalFrame.getAttribute('src') !== url) terminalFrame.src = url;
  if (!terminalDialog.open) terminalDialog.showModal();
}

document.getElementById('close-terminal').onclick = () => terminalDialog.close();
closeOnBackdrop(terminalDialog);
// A loaded frame stays attached, and tmux sizes every window to its smallest client — including one nobody
// is looking at.
terminalDialog.addEventListener('close', () => { terminalFrame.src = 'about:blank'; });

const resumeForm = document.getElementById('resume');
document.getElementById('resume-task').onclick = () =>
  toggleForm(resumeForm, document.getElementById('resume-task'),
    () => document.getElementById('resume-url').focus());
resumeForm.onsubmit = async (event) => {
  event.preventDefault();
  const state = document.getElementById('resume-state');
  const button = resumeForm.querySelector('button[type=submit]');
  button.disabled = true;
  state.textContent = 'reading the review request…';        // a model call unless a CodeHost is configured
  try {
    const result = await api('/api/tasks/resume', {
      method: 'POST',
      headers: {'Content-Type': 'application/json'},
      body: JSON.stringify({reviewRequestUrl: document.getElementById('resume-url').value}),
    });
    toast(result.message);
    resumeForm.reset();
    resumeForm.hidden = true;
  } catch (e) {
    toast(e.message, true);
  } finally {
    button.disabled = false;
    state.textContent = '';
    await load();
  }
};

async function openReport(title, path) {
  try {
    showReport(title, await text(path));
  } catch (e) {
    toast(e.message, true);
  }
}

// A <dialog> closes on Escape by itself; the inline forms do not.
document.addEventListener('keydown', (event) => {
  if (event.key !== 'Escape') return;
  for (const [id, button] of [['resume', 'resume-task'], ['palette', 'open-palette']]) {
    const form = document.getElementById(id);
    if (form && !form.hidden) {
      form.hidden = true;
      document.getElementById(button).setAttribute('aria-pressed', 'false');
    }
  }
});
