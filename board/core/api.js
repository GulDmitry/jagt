// A failure carries both: the code a caller acts on and the sentence a human reads.

const failed = (response, refused) => {
  const failure = new Error(refused.error || `${response.status} ${response.statusText}`);
  failure.code = refused.code;
  return failure;
};

const parsed = (said) => {
  try {
    const refused = JSON.parse(said);
    return refused?.error ? refused : {error: said};
  } catch {
    return {error: said};
  }
};

export async function api(path, options) {
  const response = await fetch(path, options);
  const body = await response.json().catch(() => ({}));
  if (!response.ok) throw failed(response, body);
  return body;
}

export async function text(path, options) {
  const response = await fetch(path, options);
  const body = await response.text();
  if (!response.ok) throw failed(response, parsed(body));
  return body;
}

const post = (path, param, id, body) => api(`/api/${path}?${param}=${encodeURIComponent(id)}`, {
  method: 'POST',
  headers: {'Content-Type': 'application/json'},
  body: JSON.stringify(body),
});

// The one way a line reaches a running session, from the board's tab and from a report alike.
export const sayTo = (taskId, line) => post('tasks/say', 'task', taskId, {line});
export const sayToWindow = (name, line) => post('windows/say', 'name', name, {line});
export const runTo = (taskId, line) => post('tasks/run', 'task', taskId, {line});
export const pressKey = (taskId, key) => post('tasks/key', 'task', taskId, {key});

// Every action reloads the board: by the time such a refusal is read the view is right; say so.
const STALE_VIEW = ['NO_SUCH_TASK', 'ACTION_NOT_AVAILABLE'];

export const refusal = (e) =>
  (STALE_VIEW.includes(e.code) ? `${e.message}\n\nThe board is up to date now.` : e.message);
