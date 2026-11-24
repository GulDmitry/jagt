import { TaskList } from "../components/TaskList.js";
import { html } from "../components/html.js";
import { said } from "./keyhint.js";
import * as seen from "./seen.js";

/** @param {import("./view.js").OpenView} view */
export const list = ({ groups, shown, open, now }) => {
  const rowVerb = shown
    .flatMap((task) => task.actions || [])
    .find((action) => action.onRow);
  return html`<${TaskList}
    groups=${groups}
    selected=${open?.id}
    changed=${seen.changed}
    now=${now}
    keys=${said(rowVerb)}
  />`;
};
