// A failure carries both: the code a caller acts on and the sentence a human reads.

export async function api(path, options) {
  const response = await fetch(path, options);
  const body = await response.json().catch(() => ({}));
  if (!response.ok) {
    const failure = new Error(body.error || `${response.status} ${response.statusText}`);
    failure.code = body.code;
    throw failure;
  }
  return body;
}

export async function text(path, options) {
  const response = await fetch(path, options);
  const body = await response.text();
  if (!response.ok) throw new Error(body || `${response.status} ${response.statusText}`);
  return body;
}

// Every action reloads the board, so by the time such a refusal is read the view is already right — say so, or
// it reads as jagt refusing something it will keep refusing.
const STALE_VIEW = ['NO_SUCH_TASK', 'ACTION_NOT_AVAILABLE'];

export const refusal = (e) =>
  (STALE_VIEW.includes(e.code) ? `${e.message}\n\nThe board is up to date now.` : e.message);
