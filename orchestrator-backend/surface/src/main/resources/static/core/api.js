// The only place that talks to the backend. A failed call carries the sentence jagt refused with AND its code,
// because a caller acts on the code and a human reads the sentence.

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

// Refusals that mean "this page was describing a task that has moved on". Every action reloads the board
// afterwards, so by the time the message is read the view is already right — say so, or it reads as jagt
// refusing something it will keep refusing.
const STALE_VIEW = ['NO_SUCH_TASK', 'ACTION_NOT_AVAILABLE'];

export const refusal = (e) =>
  (STALE_VIEW.includes(e.code) ? `${e.message}\n\nThe board is up to date now.` : e.message);
