// The board. Plain DOM on purpose: no build step, no CDN, works with the machine offline — and the whole
// page is ~200 lines, which is less than the config a framework would need.
//
// It holds NO rules of its own: every card renders the server's projection (phase, owner, hint, legal
// actions), and a button posts the action id back. If the server did not list an action, there is no button —
// and if the page is stale, the POST is refused with a sentence, which is what the toast shows.

const PHASES = [
  ['BUILD', 'build'], ['REVIEW', 'review'], ['CHECK', 'check'],
  ['READY', 'ready'], ['DEPLOY', 'deploy'], ['DONE', 'done'],
];

const board = document.getElementById('board');
const toasts = document.getElementById('toasts');
const sortBy = document.getElementById('sort');
const onlyMine = document.getElementById('mine');
const live = document.getElementById('live');
let tasks = [];
let projects = [];
let renderedProjects = null;
let verbs = [];
let busy = new Set();

const relative = (millis) => {
  const seconds = Math.max(0, Math.round((Date.now() - millis) / 1000));
  if (seconds < 60) return `${seconds}s ago`;
  if (seconds < 3600) return `${Math.round(seconds / 60)}m ago`;
  if (seconds < 86400) return `${Math.round(seconds / 3600)}h ago`;
  return `${Math.round(seconds / 86400)}d ago`;
};

// FLOOR everywhere, matching DashboardRenderer.compactDuration exactly: with `orchestrator.ui=both` the two
// surfaces sit side by side, and 90 minutes reading "1h" here and "2h" there is the drift the shared
// projection exists to prevent — sharing the data is not enough if a derived number is formatted twice.
const duration = (millis) => {
  const minutes = Math.max(0, Math.floor(millis / 60000));
  if (minutes < 60) return `${minutes}m`;
  if (minutes < 1440) return `${Math.floor(minutes / 60)}h`;
  return `${Math.floor(minutes / 1440)}d`;
};

// The transitions the task actually went through, as a tooltip: the card stays one line, the record is one
// hover away. Server-sent, so it is the same history state.json holds.
const timeline = (task) => (task.history || [])
  .map((step) => `${new Date(step.at).toLocaleString()}  ${step.status}`)
  .join('\n');

const compactTokens = (n) => {
  if (!n) return '';
  if (n < 1000) return `${n} tok`;
  if (n < 999500) return `${Math.round(n / 1000)}k tok`;
  return `${(n / 1e6).toFixed(1)}M tok`;
};

// A toast is gone in seconds, so every one of them is also kept here for the session. No persistence: a
// reload starts an empty log, and the backend's own file keeps what matters longer.
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

async function api(path, options) {
  const response = await fetch(path, options);
  const body = await response.json().catch(() => ({}));
  if (!response.ok) throw new Error(body.error || `${response.status} ${response.statusText}`);
  return body;
}

// The grammar, fetched once: the palette completes and validates against the SERVER's verb list, so a command
// the console accepts can never be missing from the suggestions here.
async function loadVerbs() {
  try {
    verbs = await api('/api/commands');
  } catch (e) {
    verbs = [];                      // no suggestions is a degraded palette, not a broken one
  }
  refreshSuggestions();
}

async function load() {
  try {
    const data = await api('/api/tasks');
    tasks = data.tasks;
    projects = data.projects || [];
    fillProjects();
    document.getElementById('spend').textContent =
      data.spend.calls ? `${data.spend.calls} calls · ${compactTokens(data.spend.tokens)}` : '';
    render();
  } catch (e) {
    toast(`Cannot reach the backend: ${e.message}`, true);
  }
}

function fillProjects() {
  const select = document.getElementById('project');
  const signature = projects.join('\n');
  if (renderedProjects === signature) {
    return;                       // a rebuild collapses the list under a human who has it open
  }
  renderedProjects = signature;
  const chosen = select.value;
  select.replaceChildren(...(projects.length
    ? projects.map((p) => new Option(p, p))
    : [Object.assign(new Option('no projects in config.json', ''), {disabled: true})]));
  if (projects.includes(chosen)) {
    select.value = chosen;
  }
}

function sorted(list) {
  const copy = [...list];
  const by = sortBy.value;
  if (by === 'tokens') copy.sort((a, b) => b.tokens - a.tokens);
  else if (by === 'alias') copy.sort((a, b) => (a.alias || '').localeCompare(b.alias || ''));
  else if (by === 'title') copy.sort((a, b) => (a.title || '').localeCompare(b.title || ''));
  else copy.sort((a, b) => b.lastActiveAt - a.lastActiveAt);
  return copy;
}

function render() {
  const shown = onlyMine.checked ? tasks.filter((t) => t.owner === 'YOU') : tasks;
  const waiting = tasks.filter((t) => t.owner === 'YOU').length;
  const waitingLabel = document.getElementById('waiting');
  waitingLabel.hidden = waiting === 0;
  waitingLabel.textContent = `${waiting} waiting on you`;
  document.getElementById('empty').hidden = tasks.length > 0;
  // Only phases that HAVE tasks get a column. `done` deletes the task outright, so a DONE column could never
  // hold anything — it just sat there reading "done 0" — and five empty columns are noise on a board of two.
  board.replaceChildren(...PHASES.map(([phase, label]) => {
    const inPhase = sorted(shown.filter((task) => task.phase === phase));
    if (!inPhase.length) return null;
    const section = document.createElement('section');
    const heading = document.createElement('h2');
    heading.innerHTML = `${label} <span class="count">${inPhase.length}</span>`;
    section.append(heading, ...inPhase.map(card));
    return section;
  }).filter(Boolean));
}

function card(task) {
  const owner = task.owner.toLowerCase();
  const article = document.createElement('article');
  article.className = owner;

  const top = document.createElement('div');
  top.className = 'card-top';
  top.innerHTML = `<span class="alias">${task.alias || '-'}</span><span class="id">${task.id}</span>`;
  const badge = document.createElement('span');
  badge.className = `badge ${owner}`;
  badge.textContent = task.owner === 'YOU' ? 'your move' : task.owner.toLowerCase();
  top.append(badge);

  const title = document.createElement('div');
  title.className = 'title';
  title.textContent = task.title || '';

  const meta = document.createElement('div');
  meta.className = 'meta';
  // Two different clocks, on purpose: how long it has been in THIS status (what you want when a task is
  // waiting on you) and when it was last active at all (a keep-alive bumps only the second one).
  const status = document.createElement('span');
  status.className = 'status';
  status.textContent = `${task.status} · ${duration(Date.now() - task.statusSince)}`;
  status.title = timeline(task);
  meta.append(status);
  meta.insertAdjacentHTML('beforeend', `<span>${task.project}</span>`
    + `<span>${relative(task.lastActiveAt)}</span>`
    + (task.tokens ? `<span>${compactTokens(task.tokens)}</span>` : ''));

  const hint = document.createElement('div');
  hint.className = 'hint';
  hint.textContent = task.hint;

  const article_children = [top, title, meta, hint];

  if (task.detail) {
    const detail = document.createElement('div');
    const problem = /^(PROBLEM|NEEDS)/.test(task.detail);
    detail.className = problem ? 'detail problem' : 'detail';
    detail.textContent = task.detail;
    if (!problem && /^https?:/.test(task.detail)) detail.textContent = '';
    article_children.push(detail);
  }

  // The agent's intended answers to the review comments, sitting unread in the worktree. Nothing else on the
  // page would tell you they exist, and `ship` posts them.
  if (task.draftedReplies) {
    const drafts = document.createElement('div');
    drafts.className = 'drafts';
    drafts.textContent = 'drafted review replies — read them before you ship';
    drafts.title = 'review_replies.md in the worktree; open it with the IDE action';
    article_children.push(drafts);
  }

  const links = document.createElement('div');
  links.className = 'links';
  if (task.ticketUrl) links.append(link(task.ticketUrl, 'ticket'));
  if (task.reviewRequestUrl) links.append(link(task.reviewRequestUrl, 'review request'));
  if (links.children.length) article_children.push(links);

  const actions = document.createElement('div');
  actions.className = 'actions';
  for (const action of task.actions) {
    const button = document.createElement('button');
    button.textContent = action.label;
    button.title = action.hint;
    if (action.primary) button.className = 'primary';
    button.disabled = busy.has(task.id);
    button.onclick = () => run(task, action);
    actions.append(button);
  }
  article_children.push(actions);
  article.append(...article_children);
  return article;
}

function link(href, text) {
  const anchor = document.createElement('a');
  anchor.href = href;
  anchor.target = '_blank';
  anchor.rel = 'noreferrer';
  anchor.textContent = text;
  return anchor;
}

async function run(task, action) {
  // `done` destroys a worktree and `deploy` writes to a shared branch: ask, exactly like the console makes
  // you type the word.
  if ((action.id === 'done' || action.id === 'deploy')
      && !confirm(`${action.label} ${task.id}?\n\n${action.hint}`)) {
    return;
  }
  busy.add(task.id);
  render();
  try {
    const result = await api(`/api/tasks/${encodeURIComponent(task.id)}/actions/${action.id}`, {method: 'POST'});
    toast(result.message);
  } catch (e) {
    toast(e.message, true);
  } finally {
    busy.delete(task.id);
    await load();
  }
}

// New task: the same modifiers the `do` grammar takes, as fields.
const launchForm = document.getElementById('launch');

// One control per form: the header button opens it and closes it, and says which state it is in. Escape closes
// whatever is open. Nothing else — an inline form does not need a second button explaining itself.
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
        project: document.getElementById('project').value,
        mode: document.getElementById('plan').checked ? 'plan' : null,
        baseBranch: document.getElementById('base-branch').value,
        notes: document.getElementById('notes').value,
      }),
    });
    toast(result.message);
    launchForm.reset();
    launchForm.hidden = true;
  } catch (e) {
    toast(e.message, true);
  } finally {
    button.disabled = false;
    state.textContent = '';
    await load();
  }
};

sortBy.onchange = render;
onlyMine.onchange = render;

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
document.addEventListener('keydown', (event) => {
  if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 'k') {
    event.preventDefault();
    togglePalette(palette.hidden);
  } else if (event.key === 'Escape' && !palette.hidden) {
    togglePalette(false);
  }
});

// What the human typed, understood WITHOUT a model: a known verb, and — for the per-task ones — a task that
// actually exists. Anything else is left to tier 2, which is what the model is for.
function parseCommand(line) {
  const tokens = line.trim().split(/\s+/).filter(Boolean);
  if (!tokens.length) return null;
  const verb = verbs.find((v) => v.id === tokens[0].toLowerCase());
  if (!verb) return null;
  const argument = tokens.slice(1).join(' ');
  if (!verb.takesTask) return {verb, argument};
  const task = tasks.find((t) => t.id === argument || (t.alias || '') === argument);
  return {verb, argument, task};
}

function refreshSuggestions() {
  document.getElementById('ask-options').replaceChildren(
    ...verbs.map((verb) => Object.assign(document.createElement('option'),
      {value: verb.id, label: verb.hint})));
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
    const known = verbs.some((v) => v.id === word.toLowerCase());
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
    await run(task, {id: verb.id, label: verb.id, hint: verb.hint});
    return `${verb.id} ${task.alias || task.id}`;
  }
  if (verb.id === 'help') { showReport('help — command reference', await text('/api/help')); return 'help'; }
  if (verb.id === 'stats') { showReport('stats — token spend', await text('/api/stats')); return 'stats'; }
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
  if (parsed && (!parsed.verb.takesTask || parsed.task)) {
    button.disabled = true;
    try {
      const ran = await runParsed(parsed);
      if (ran) { ask.value = ''; togglePalette(false); return; }
    } catch (e) {
      toast(e.message, true);
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
    // The answer always leads with what it understood, so a wrong mapping is visible rather than mysterious.
    toast(result.message);
    ask.value = '';
    togglePalette(false);
  } catch (e) {
    toast(e.message, true);
  } finally {
    button.disabled = false;
    state.textContent = '';
    await load();
  }
};

// ---- everything the console can do that is not a per-task button ----
// Parity is the rule, not a nice-to-have: a capability that exists in one surface only is the bug this section
// closes (resume, stats, help, stop). Per-task verbs — ship, review, ide (incl. the DEPLOY_CONFLICT
// worktree), deploy, revert, respawn, focus, done — are already the card's own buttons, because the server
// lists them per task and the board renders exactly that list.

const report = document.getElementById('report');
const reportTitle = document.getElementById('report-title');
const reportBody = document.getElementById('report-body');

// One dialog for every plain-text answer the backend gives (help, stats, orphans). A native <dialog>
// costs nothing, dims the board itself, closes on Escape, and — unlike a new tab — cannot be lost behind the
// window you were already reading.
function showReport(title, text) {
  reportTitle.textContent = title;
  reportBody.textContent = text.trimEnd();
  if (!report.open) report.showModal();
  reportBody.scrollTop = 0;
}

document.getElementById('close-report').onclick = () => report.close();
document.getElementById('show-log').onclick = () => showReport('log — this session', messages.join('\n'));

async function text(path, options) {
  const response = await fetch(path, options);
  const body = await response.text();
  if (!response.ok) throw new Error(body || `${response.status} ${response.statusText}`);
  return body;
}

// `resume`: take over a review request that already exists (reopened, or someone else's work).
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

document.getElementById('show-stats').onclick = () => openReport('stats — token spend', '/api/stats');
document.getElementById('show-help').onclick = () => openReport('help — command reference', '/api/help');
// The orphan report is the same plain text /orphans has always served; it just no longer costs you a tab.
document.getElementById('show-orphans').onclick = () => openReport('orphaned worktrees', '/orphans');

async function openReport(title, path) {
  try {
    showReport(title, await text(path));
  } catch (e) {
    toast(e.message, true);
  }
}

// A <dialog> closes on Escape by itself; the inline forms do not, and that is what the old "Cancel" buttons
// were standing in for.
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
