// A failure carries both: the code a caller acts on and the sentence a human reads.

/**
 * @param {Response} response
 * @param {{ error?: string, code?: string }} refused
 * @returns {Error & { code?: string }}
 */
const failed = (response, refused) => {
  /** @type {Error & { code?: string }} */
  const failure = new Error(
    refused.error || `${response.status} ${response.statusText}`,
  );
  failure.code = refused.code;
  return failure;
};

/** @param {string} said */
const parsed = (said) => {
  try {
    const refused = JSON.parse(said);
    return refused?.error ? refused : { error: said };
  } catch {
    return { error: said };
  }
};

/**
 * @param {string} path
 * @param {RequestInit} [options]
 */
export async function api(path, options) {
  const response = await fetch(path, options);
  const body = await response.json().catch(() => ({}));
  if (!response.ok) throw failed(response, body);
  return body;
}

/**
 * @param {string} path
 * @param {RequestInit} [options]
 */
export async function text(path, options) {
  const response = await fetch(path, options);
  const body = await response.text();
  if (!response.ok) throw failed(response, parsed(body));
  return body;
}

/**
 * @param {string} path
 * @param {string} param
 * @param {string} id
 * @param {object} body
 */
const post = (path, param, id, body) =>
  api(`/api/${path}?${param}=${encodeURIComponent(id)}`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });

// The one way a line reaches a running session, from the board's tab and from a report alike.
/** @param {string} taskId @param {string} line */
export const sayTo = (taskId, line) =>
  post("tasks/say", "task", taskId, { line });
/** @param {string} name @param {string} line */
export const sayToWindow = (name, line) =>
  post("windows/say", "name", name, { line });
/** @param {string} taskId @param {string} line */
export const runTo = (taskId, line) =>
  post("tasks/run", "task", taskId, { line });
/** @param {string} taskId @param {string} key */
export const pressKey = (taskId, key) =>
  post("tasks/key", "task", taskId, { key });

// Every action reloads the board: by the time such a refusal is read the view is right; say so.
const STALE_VIEW = ["NO_SUCH_TASK", "ACTION_NOT_AVAILABLE"];

/** @param {unknown} e */
export const refusal = (e) => {
  const failure = e instanceof Error ? e : new Error(String(e));
  const code =
    "code" in failure && typeof failure.code === "string" ? failure.code : "";
  return STALE_VIEW.includes(code)
    ? `${failure.message}\n\nThe board is up to date now.`
    : failure.message;
};

/** @param {unknown} e */
export const cause = (e) => (e instanceof Error ? e.message : String(e));
