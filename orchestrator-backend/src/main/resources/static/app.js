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
let capacity = 0;
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

function toast(message, isError) {
  const node = document.createElement('div');
  node.className = isError ? 'toast error' : 'toast';
  node.textContent = message;
  toasts.append(node);
  setTimeout(() => node.remove(), isError ? 12000 : 7000);
}

async function api(path, options) {
  const response = await fetch(path, options);
  const body = await response.json().catch(() => ({}));
  if (!response.ok) throw new Error(body.error || `${response.status} ${response.statusText}`);
  return body;
}

async function load() {
  try {
    const data = await api('/api/tasks');
    tasks = data.tasks;
    projects = data.projects;
    capacity = data.capacity;
    document.getElementById('spend').textContent =
      data.spend.calls ? `${data.spend.calls} calls · ${compactTokens(data.spend.tokens)}` : '';
    render();
  } catch (e) {
    toast(`Cannot reach the backend: ${e.message}`, true);
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
  // The cap is only useful BEFORE a New task is refused for hitting it, so it sits in the header, not in the
  // error. `full` is what turns it red — the same threshold the backend enforces.
  const slots = document.getElementById('slots');
  slots.hidden = capacity === 0;
  slots.textContent = `${tasks.length}/${capacity} slots`;
  slots.classList.toggle('full', capacity > 0 && tasks.length >= capacity);

  board.replaceChildren(...PHASES.map(([phase, label]) => {
    const inPhase = sorted(shown.filter((task) => task.phase === phase));
    const section = document.createElement('section');
    const heading = document.createElement('h2');
    heading.innerHTML = `${label} <span class="count">${inPhase.length}</span>`;
    section.append(heading, ...inPhase.map(card));
    return section;
  }));
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
document.getElementById('new-task').onclick = () => {
  launchForm.hidden = !launchForm.hidden;
  const select = document.getElementById('project');
  select.replaceChildren(new Option('project…', ''), ...projects.map((p) => new Option(p, p)));
  if (!launchForm.hidden) document.getElementById('ref').focus();
};
document.getElementById('cancel-launch').onclick = () => { launchForm.hidden = true; };
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
const events = new EventSource('/api/events');
events.addEventListener('open', () => live.classList.add('on'));
events.addEventListener('changed', load);
events.onerror = () => live.classList.remove('on');
setInterval(render, 15000);
load();
