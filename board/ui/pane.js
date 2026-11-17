import { Pane } from "../components/Pane.js";
import { html } from "../components/html.js";
import { blockedOn, offersOn } from "./offers.js";
import * as repliesOpened from "./repliesOpened.js";
import * as tabs from "./tabs.js";

/**
 * @param {import("./view.js").OpenView} view
 * @param {() => void} repaint
 */
export const pane = ({ open, manyProjects, flagged, now }, repaint) =>
  html`<${Pane}
    card=${{
      task: open,
      manyProjects,
      flagged,
      blocked: blockedOn(open),
      offers: offersOn(open),
      now,
    }}
    tab=${tabs.current()}
    shown=${tabs.shown(open, repaint)}
    peek=${tabs.peek(open)}
    fresh=${repliesOpened.fresh(open)}
  />`;
