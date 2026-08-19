// Plain DOM on purpose: no build step, no CDN, works with the machine offline.
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
const projectSelect = document.getElementById('project');
let tasks = [];
let projects = [];
let autoReview = {summary: '', enabled: false};
let renderedProjects = null;
// An untouched default is not a decision, and naming a project SKIPS the ticket read — the escape hatch a
// typed `do ABC-1 <project>` deliberately takes. So the list opens on a real project, and only a pick is sent.
let projectPicked = false;
projectSelect.onchange = () => { projectPicked = true; };
let verbs = [];
let busy = new Set();

// Every piece of a card is BUILT, never interpolated into markup: ids, aliases and project keys come out of a
// state file the human is invited to edit by hand, and an assumption about their shape is invisible from here.
const span = (className, text) => {
  const node = document.createElement('span');
  if (className) node.className = className;
  node.textContent = text;
  return node;
};

const relative = (millis) => {
  const seconds = Math.max(0, Math.round((Date.now() - millis) / 1000));
  if (seconds < 60) return `${seconds}s ago`;
  if (seconds < 3600) return `${Math.round(seconds / 60)}m ago`;
  if (seconds < 86400) return `${Math.round(seconds / 3600)}h ago`;
  return `${Math.round(seconds / 86400)}d ago`;
};

// FLOOR everywhere, matching DurationFormat.compact exactly: with `orchestrator.ui=both` the two
// surfaces sit side by side, and 90 minutes reading "1h" here and "2h" there is the drift the shared
// projection exists to prevent — sharing the data is not enough if a derived number is formatted twice.
const duration = (millis) => {
  const minutes = Math.max(0, Math.floor(millis / 60000));
  if (minutes < 60) return `${minutes}m`;
  if (minutes < 1440) return `${Math.floor(minutes / 60)}h`;
  return `${Math.floor(minutes / 1440)}d`;
};

// A countdown is watched ticking, unlike an age in a column: seconds matter while the wait is under a minute.
const countdown = (millis) => {
  const seconds = Math.max(0, Math.floor(millis / 1000));
  if (seconds < 60) return `${seconds}s`;
  // CEILING, mirroring DurationFormat.countdown: a ten-minute wait must not read "9m" for its first minute.
  const minutes = Math.ceil(seconds / 60);
  return minutes < 60 ? `${minutes}m` : `${Math.ceil(minutes / 60)}h`;
};

// What the unattended poller is about to do with this task, or nothing when it is not its business. The WORDS are
// the server's (watch.note), so the console and this cannot drift; only the countdown is formatted here, from the
// absolute stamp, so the slow repaint keeps it honest without another fetch.
const watchLine = (watch) => {
  if (!watch || !watch.note) return null;
  if (watch.state === 'WATCHING') {
    const remaining = watch.nextPollAt - Date.now();
    return {text: `${watch.note} ${remaining <= 0 ? 'due now' : `in ${countdown(remaining)}`}`};
  }
  return {text: watch.note, stalled: true};
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

async function api(path, options) {
  const response = await fetch(path, options);
  const body = await response.json().catch(() => ({}));
  if (!response.ok) {
    const failure = new Error(body.error || `${response.status} ${response.statusText}`);
    failure.code = body.code;
    throw failure;
  }
  return body;
}

// Refusals that mean "this page was describing a task that has moved on". Every action reloads the board
// afterwards, so by the time the message is read the view is already right — say so, or it reads as jagt
// refusing something it will keep refusing.
const STALE_VIEW = ['NO_SUCH_TASK', 'ACTION_NOT_AVAILABLE'];

const refusal = (e) => (STALE_VIEW.includes(e.code) ? `${e.message}\n\nThe board is up to date now.` : e.message);

// Fetched, not hardcoded: the palette completes and validates against the SERVER's verb list, so a command the
// console accepts can never be missing from the suggestions here.
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
    autoReview = {summary: data.autoReview, enabled: data.autoReviewEnabled};
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
  const signature = projects.join('\n');
  if (renderedProjects === signature) {
    return;                       // a rebuild collapses the list under a human who has it open
  }
  renderedProjects = signature;
  const chosen = projectSelect.value;
  projectSelect.replaceChildren(...(projects.length
    ? projects.map((p) => new Option(p, p))
    : [Object.assign(new Option('no projects in config.json', ''), {disabled: true})]));
  if (projects.includes(chosen)) {
    projectSelect.value = chosen;
  } else {
    projectPicked = false;
  }
}

function sorted(list) {
  const copy = [...list];
  const by = sortBy.value;
  if (by === 'alias') copy.sort((a, b) => (a.alias || '').localeCompare(b.alias || ''));
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
  const chip = document.getElementById('auto-review');
  chip.textContent = autoReview.summary || '';
  chip.classList.toggle('on', autoReview.enabled);
  document.getElementById('empty').hidden = tasks.length > 0;
  // Only phases that HAVE tasks get a column: `done` deletes the task outright, so a DONE column could never
  // hold anything, and empty columns are noise on a board of two.
  board.replaceChildren(...PHASES.map(([phase, label]) => {
    const inPhase = sorted(shown.filter((task) => task.phase === phase));
    if (!inPhase.length) return null;
    const section = document.createElement('section');
    const heading = document.createElement('h2');
    heading.append(`${label} `, span('count', inPhase.length));
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
  top.append(span('alias', task.alias || '-'), span('id', task.id));
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
  // waiting on you) and when the session last did anything (a keep-alive bumps only the second one). The
  // second one is LABELLED — two bare "…d ago" in one row read as two ages of the same thing.
  const status = document.createElement('span');
  status.className = 'status';
  status.textContent = `${task.status} · ${duration(Date.now() - task.statusSince)}`;
  status.title = timeline(task);
  // One session, one or more repositories: naming them all is what tells you this task moves two codebases.
  const repos = task.repos || [];
  const where = repos.length > 1 ? repos.map((r) => r.project).join(' + ') : task.project;
  meta.append(status, span(null, where), span(null, `active ${relative(task.lastActiveAt)}`));
  // The checks, as one dot: the sweep already reads the pipeline, and a red run while the card still says
  // CI_POLLING is the thing a status word cannot show.
  if (task.pipeline && task.pipeline !== 'NONE') {
    const checks = span(`checks ${task.pipeline.toLowerCase()}`, '');
    checks.title = `checks: ${task.pipelineSaid || task.pipeline.toLowerCase()}`;
    meta.append(checks);
  }

  const hint = document.createElement('div');
  hint.className = 'hint';
  hint.textContent = task.hint;

  const article_children = [top, title, meta, hint];

  // A detail that is nothing but a URL is the request, and that already has its own row below.
  if (task.detail && !/^https?:/.test(task.detail)) {
    const detail = document.createElement('div');
    detail.className = /^(PROBLEM|NEEDS)/.test(task.detail) ? 'detail problem' : 'detail';
    detail.textContent = task.detail;
    article_children.push(detail);
  }

  // Nothing else on the page would say the drafted answers exist, and `ship` is what posts them.
  if (task.draftedReplies) {
    const drafts = document.createElement('div');
    drafts.className = 'drafts';
    drafts.textContent = 'drafted review replies — read them before you ship';
    drafts.title = 'review_replies.md in the worktree; open it with the IDE action';
    article_children.push(drafts);
  }

  const watch = watchLine(task.autoReview);
  if (watch) {
    const node = document.createElement('div');
    node.className = watch.stalled ? 'watch stalled' : 'watch';
    node.textContent = watch.text;
    node.title = autoReview.summary;
    article_children.push(node);
  }

  const links = document.createElement('div');
  links.className = 'links';
  if (task.ticketUrl) links.append(link(task.ticketUrl, 'ticket'));
  if (repos.length > 1) {
    // Each repository has its own request, its own reviewers and its own diff, so one link cannot stand for
    // the task: an unlabelled second link would be indistinguishable from the first.
    for (const repo of repos.filter((r) => r.reviewRequestUrl)) {
      links.append(link(repo.reviewRequestUrl, `${repo.project} request`));
    }
  } else if (task.reviewRequestUrl) {
    links.append(link(task.reviewRequestUrl, 'review request'));
  }
  if (links.children.length) article_children.push(links);

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
    button.title = action.hint;
    if (action.primary) button.className = 'primary';
    button.disabled = busy.has(task.id);
    button.onclick = () => run(task, action);
    row.append(button);
  }
  article.append(...article_children);
  return article;
}

function actionRow(group) {
  const row = document.createElement('div');
  row.className = `actions ${group}`;
  row.dataset.group = group;
  return row;
}

function link(href, text) {
  const anchor = document.createElement('a');
  anchor.href = href;
  anchor.target = '_blank';
  anchor.rel = 'noreferrer';
  anchor.textContent = text;
  return anchor;
}

// A deploy is the one click that writes a branch other people build on, so the question names the exact writes
// it is asking for — one line per repository, since a task can move several and each has its own target.
const deployQuestion = (task) => {
  const lands = (task.repos || []).map((repo) =>
    `${repo.project} → ${repo.deployBranch || 'no deployBranch in config.json'}`);
  // A deploy lands what was SHIPPED. After a round the agent's fixes sit uncommitted in the worktree and its
  // drafted answers are unposted, and only `ship` moves either — so the question says so rather than letting a
  // click quietly deploy the previous round and mark the task DEPLOYED.
  const unshipped = task.status === 'REVIEW_PENDING' || task.draftedReplies
    ? '\n\nCareful: this task has work that was never shipped — the deploy lands the last SHIP, not the agent\u2019s'
      + ' latest changes, and drafted replies stay unposted. Ship first to include them.'
    : '';
  return `Deploy ${task.id}?\n\nThis merges and pushes:\n${lands.join('\n')}${unshipped}`;
};

async function run(task, action) {
  // `done` destroys a worktree and `deploy` writes to a shared branch: ask.
  const question = action.id === 'deploy' ? deployQuestion(task) : `${action.label} ${task.id}?\n\n${action.hint}`;
  if ((action.id === 'done' || action.id === 'deploy') && !confirm(question)) {
    return;
  }
  busy.add(task.id);
  render();
  try {
    const result = await api(`/api/tasks/${encodeURIComponent(task.id)}/actions/${action.id}`, {method: 'POST'});
    toast(result.message);
    if (action.id === 'focus') await openTerminal(task);
  } catch (e) {
    toast(refusal(e), true);
  } finally {
    busy.delete(task.id);
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

// A verb answers to its own name and to whatever it was renamed from: accepted here as the console accepts it,
// and offered in neither.
function verbFor(word) {
  const typed = word.toLowerCase();
  // Its own name first, exactly as the server resolves it: an alias must never shadow another verb's id.
  return verbs.find((v) => v.id === typed) || verbs.find((v) => (v.aliases || []).includes(typed));
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
  const task = tasks.find((t) => t.id === argument || (t.alias || '') === argument);
  return {verb, argument, task};
}

function refreshSuggestions() {
  document.getElementById('ask-options').replaceChildren(
    ...verbs.map((verb) => Object.assign(document.createElement('option'),
      {value: verb.id, label: verb.hint})));
  // One button per report the SERVER declares, so a new one appears in the toolbar without this page learning
  // its name.
  document.getElementById('reports').replaceChildren(...verbs.filter((verb) => verb.report).map((verb) => {
    const button = document.createElement('button');
    button.id = `show-${verb.id}`;
    button.textContent = verb.id.charAt(0).toUpperCase() + verb.id.slice(1);
    button.title = verb.hint;
    button.onclick = () => openReport(`${verb.id} — ${verb.hint}`, `/api/commands/${verb.id}`);
    return button;
  }));
  const resume = verbFor('resume');
  if (resume) document.getElementById('resume-task').title = resume.hint;
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
    await run(task, {id: verb.id, label: verb.id, hint: verb.hint});
    return `${verb.id} ${task.alias || task.id}`;
  }
  // Reports are named by the server rather than listed here, so one more needs no branch in this page.
  if (verb.report) {
    showReport(`${verb.id} — ${verb.hint}`, await text(`/api/commands/${encodeURIComponent(verb.id)}`));
    return verb.id;
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

async function text(path, options) {
  const response = await fetch(path, options);
  const body = await response.text();
  if (!response.ok) throw new Error(body || `${response.status} ${response.statusText}`);
  return body;
}

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
