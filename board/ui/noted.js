import * as repliesOpened from "./repliesOpened.js";
import * as seen from "./seen.js";
import * as selection from "./selection.js";
import * as tabs from "./tabs.js";

/** @param {import("./view.js").View} view */
export function noted({ tasks, open }) {
  seen.note(tasks, selection.paneShown() ? open : null);
  if (open && selection.paneShown() && tabs.current() === "round")
    repliesOpened.note(open);
}
