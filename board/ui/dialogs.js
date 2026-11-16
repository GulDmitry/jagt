import { refusal, text } from "../core/api.js";
import { follow, stopWaiting } from "./followup.js";
import * as dialog from "./reportdialog.js";
import { toast } from "./toast.js";

// Two reports opened in quick succession: the one asked for last is the one shown, whichever answers last.
// A poll and the board's own event overlap: the read sent last paints.
const state = {
  showing: { path: null, about: null, body: "", extra: null },
  asked: 0,
  reread: 0,
};

export function showReport(title, said, extra = null) {
  state.asked++;
  state.showing = { path: null, about: null, body: said, extra };
  stopWaiting();
  dialog.paint(title, said, state.showing);
}

/** False when the report could not be read, the caller deciding what a failed read costs it. */
export async function openReport(
  title,
  path,
  { extra = null, about = null } = {},
) {
  const mine = ++state.asked;
  try {
    const said = await text(path);
    if (mine !== state.asked) return true;
    state.showing = { path, about, body: said, extra };
    stopWaiting();
    dialog.paint(title, said, state.showing);
    // Whatever changed WHILE this was being read reached no open dialog, so it is read once more now.
    await repaintReport();
    return true;
  } catch (e) {
    if (mine !== state.asked) return true;
    toast(refusal(e), true);
    return false;
  }
}

/** The board changed under an open report: read it again, and stop waiting once it says something new. */
export async function repaintReport() {
  if (!dialog.isOpen() || !state.showing.path) {
    return;
  }
  // What an extra shows may change with no word of the report changing.
  state.showing.extra?.dispatchEvent(new Event("reread"));
  const read = state.showing.path;
  const mine = ++state.reread;
  let said;
  try {
    said = await text(read);
  } catch {
    return; // a read that failed is not an empty report: leave what is on screen
  }
  // The answer to a report nobody is reading any more belongs to no dialog on screen.
  if (
    mine !== state.reread ||
    !dialog.isOpen() ||
    state.showing.path !== read ||
    said === state.showing.body
  ) {
    return;
  }
  state.showing = { ...state.showing, body: said };
  dialog.redraw(said);
  stopWaiting();
}

follow({ about: () => state.showing.about, repaint: repaintReport });
dialog.onClose(stopWaiting);
