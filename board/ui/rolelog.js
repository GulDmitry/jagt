import { api } from "../core/api.js";

/** @type {{ role: string | null, lines: string[], next: number, repaint: () => void }} */
const state = { role: null, lines: [], next: 0, repaint: () => {} };

/** @param {() => void} repaint */
export const onChange = (repaint) => {
  state.repaint = repaint;
};

export const open = () => state.role;
export const lines = () => state.lines;

/** @param {string} role */
export async function follow(role) {
  if (state.role !== role) return;
  const slice = await api(`/api/roles/${role}/log?after=${state.next}`).catch(
    () => null,
  );
  if (!slice || state.role !== role) return;
  state.lines = state.lines.concat(slice.lines);
  state.next = slice.next;
  state.repaint();
}

// A second press on the open role hides it.
/** @param {string} role */
export function toggle(role) {
  if (state.role === role) {
    close();
    state.repaint();
    return;
  }
  Object.assign(state, { role, lines: [], next: 0 });
  state.repaint();
  follow(role);
}

export const close = () => {
  Object.assign(state, { role: null, lines: [], next: 0 });
};
