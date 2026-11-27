import { text, api, cause } from "../core/api.js";
import { t } from "../core/i18n.js";
import * as store from "../core/store.js";

/** @typedef {import("../core/store.js").Task} Task */
/** @typedef {import("../core/store.js").Verb} Verb */
/** @typedef {import("../components/Pane.js").Shown} Shown */
/** @typedef {import("../components/Pane.js").Reading} Reading */
/** @typedef {import("../components/Pane.js").Peek & { id?: string }} Peeked */

/** @type {{ tab: string, read: { key: string | null, shown: Shown, stale: boolean }, asked: number, windowName: string | null, peeked: Peeked, again: () => void, viewer: Reading | null, picked: Map<string, string> }} */
const state = {
  tab: "overview",
  read: { key: null, shown: null, stale: false },
  asked: 0,
  windowName: null,
  peeked: {},
  again: () => {},
  viewer: null,
  picked: new Map(),
};

/** @param {Task} task */
const artifacts = async (task) => {
  const id = encodeURIComponent(task.id);
  const names = await api(`/api/tasks/files?task=${id}`);
  const name = names.includes(state.picked.get(task.id))
    ? state.picked.get(task.id)
    : names[0];
  const rendered = name
    ? await api(`/api/tasks/file?task=${id}&name=${encodeURIComponent(name)}`)
    : null;
  return { names, name, html: rendered ? rendered.html : "" };
};

/**
 * @param {Task} task
 * @param {() => void} repaint
 */
function glance(task, repaint) {
  if (state.tab === "session") return;
  api(`/api/tasks/screen?task=${encodeURIComponent(task.id)}`).then(
    ({ held, shown: screen }) => {
      state.peeked = {
        id: task.id,
        held,
        line: screen.trimEnd().split("\n").pop().trim(),
      };
      repaint();
    },
    () => {},
  );
}

/** @param {Task} task */
export const peek = (task) => (state.peeked.id === task.id ? state.peeked : {});

/** @param {string | null} name */
export const pickWindow = (name) => {
  state.windowName = name;
  forget();
};

/** @type {Record<string, (task: Task, offer?: Verb) => Promise<Shown>>} */
const sources = {
  artifacts,
  diff: (task) => api(`/api/tasks/diff?task=${encodeURIComponent(task.id)}`),
  session: async () =>
    (await import("../terminal/session.js")).sessionOf(state.windowName),
  round: (task, offer) =>
    text(
      `/api/commands/${offer?.id}?about=${encodeURIComponent(task.alias || task.id)}`,
    ),
};

export const current = () => state.tab;

/**
 * @param {string} task
 * @param {string} name
 */
export const pick = (task, name) => {
  state.picked.set(task, name);
  forget();
};

export const forget = () => {
  state.asked++;
  state.read = { ...state.read, stale: true };
};

const EVERY = 5000;

setInterval(() => state.again(), EVERY);

/** @param {string} id */
export const choose = (id) => {
  state.tab = id;
  if (id === "code" && state.viewer?.failure) state.viewer = null;
  if (id === "diff") forget();
};

/**
 * @param {Task} task
 * @param {() => void} repaint
 */
export function shown(task, repaint) {
  state.again = () =>
    state.tab === "session" ? (forget(), repaint()) : glance(task, repaint);
  if (state.peeked.id !== task.id) glance(task, repaint);
  if (state.tab === "code") {
    if (!state.viewer)
      import("../viewer/Code.js").then(
        (loaded) => {
          state.viewer = loaded;
          repaint();
        },
        (e) => {
          state.viewer = { failure: t("tabs.unread", { cause: cause(e) }) };
          repaint();
        },
      );
    return state.viewer;
  }
  const source = sources[state.tab];
  if (!source || !task) return null;
  const offer = store.offerFor(task, state.tab);
  if (state.tab === "round" && !offer) return null;
  const key = `${state.tab}:${task.id}:${task.statusSince}`;
  if (state.read.key === key && !state.read.stale) return state.read.shown;
  const kept = state.read.key === key ? state.read.shown : null;
  const mine = ++state.asked;
  state.read = { key, shown: kept, stale: false };
  source(task, offer)
    .then((said) => {
      if (mine !== state.asked) return;
      state.read = { key, shown: said, stale: false };
      repaint();
    })
    .catch((e) => {
      if (mine !== state.asked) return;
      state.read = {
        key,
        shown: t("tabs.unread", { cause: cause(e) }),
        stale: false,
      };
      repaint();
    });
  return kept;
}
