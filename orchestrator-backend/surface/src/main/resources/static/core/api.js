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

// Every action reloads the board, so by the time such a refusal is read the view is already right — say so, or
// it reads as jagt refusing something it will keep refusing.
const STALE_VIEW = ['NO_SUCH_TASK', 'ACTION_NOT_AVAILABLE'];

export const refusal = (e) =>
  (STALE_VIEW.includes(e.code) ? `${e.message}\n\nThe board is up to date now.` : e.message);
