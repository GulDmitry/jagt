import { text, api } from "../core/api.js";
import { t } from "../core/i18n.js";
import * as store from "../core/store.js";

const state = {
  tab: "overview",
  read: { key: null, shown: null, stale: false },
  asked: 0,
  windowName: null,
  peeked: {},
  again: () => {},
  viewer: null,
};

const picked = new Map();

const artifacts = async (task) => {
  const id = encodeURIComponent(task.id);
  const names = await api(`/api/tasks/files?task=${id}`);
  const name = names.includes(picked.get(task.id))
    ? picked.get(task.id)
    : names[0];
  const rendered = name
    ? await api(`/api/tasks/file?task=${id}&name=${encodeURIComponent(name)}`)
    : null;
  return { names, name, html: rendered ? rendered.html : "" };
};

// The task's session, or a window jagt opened that is no task's.
async function sessionOf(task) {
  const names = await api("/api/windows").catch(() => []);
  const name = names.includes(state.windowName) ? state.windowName : null;
  const path = name
    ? `/api/windows/screen?name=${encodeURIComponent(name)}`
    : `/api/tasks/screen?task=${encodeURIComponent(task.id)}`;
  const screen = await api(path).catch((e) => ({
    held: true,
    shown: t("tabs.unread", { cause: e.message }),
  }));
  return { ...screen, names, name };
}

// The Session tab reads the whole screen itself.
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

export const peek = (task) => (state.peeked.id === task.id ? state.peeked : {});

export const pickWindow = (name) => {
  state.windowName = name;
  forget();
};

const sources = {
  artifacts,
  diff: (task) => api(`/api/tasks/diff?task=${encodeURIComponent(task.id)}`),
  session: sessionOf,
  round: (task, offer) =>
    text(
      `/api/commands/${offer.id}?about=${encodeURIComponent(task.alias || task.id)}`,
    ),
};

export const current = () => state.tab;

export const pick = (task, name) => {
  picked.set(task, name);
  forget();
};

export const forget = () => {
  state.asked++;
  state.read = { ...state.read, stale: true };
};

// A screen changes with no event, so it is read again while open.
const EVERY = 5000;

setInterval(() => state.again(), EVERY);

export const choose = (id) => {
  state.tab = id;
  if (id === "code" && state.viewer?.failure) state.viewer = null;
  if (id === "diff") forget();
};

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
          state.viewer = { failure: t("tabs.unread", { cause: e.message }) };
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
  // A re-read keeps the last screen up, never another task's or tab's.
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
        shown: t("tabs.unread", { cause: e.message }),
        stale: false,
      };
      repaint();
    });
  return kept;
}
