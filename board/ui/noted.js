import * as repliesOpened from "./repliesOpened.js";
import * as seen from "./seen.js";
import * as selection from "./selection.js";
import * as tabs from "./tabs.js";

export function noted({ tasks, open }) {
  // Wide, the open task is on screen unpicked, so seen; narrow, only once opened.
  seen.note(tasks, selection.paneShown() ? open : null);
  if (open && selection.paneShown() && tabs.current() === "round")
    repliesOpened.note(open);
}
