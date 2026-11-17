import { html } from "../components/html.js";
import { Pane } from "../components/Pane.js";
import { grouped, TaskList } from "../components/TaskList.js";
import { cards } from "./cards.js";
import { State } from "./State.js";

const DRAFTED = [
  "1 · FIXED · Renamed the helper.",
  "  > why is this public?",
  "2 · QUESTION · Is the cap per project? https://example.com/ABC-42.",
  "3 · PUSHED BACK · The ring forbids it.",
].join("\n");

const keys =
  "j k walk · ↵ open · n next yours · f focus · t new task · / filter · ⌘K command";
const tasks = cards.map(({ props }) => props.task);
/** @param {import("../core/store.js").Task} task */
const changed = (task) => task.alias === "a3";

export const listSection = html`<section aria-label="Task list">
  <h2>Task list</h2>
  <main>
    <${State} name="one project: yours first, a3 changed since it was opened, a4 selected">
      <${TaskList} groups=${grouped(tasks, false)} selected="ABC-70" changed=${changed} now=${Date.now()}/></${State}>
    <${State} name="the keys line under the list"><${TaskList} groups=${grouped(tasks.slice(0, 2), false)} selected="ABC-42"
      changed=${() => false} now=${Date.now()} keys=${keys}/></${State}>
    <${State} name="several projects"><${TaskList} groups=${grouped(tasks, true)} selected="ABC-42"
      changed=${() => false} now=${Date.now()}/></${State}>
    <${State} name="the open task, overview"><${Pane} card=${cards[0].props} tab="overview" shown=${null}/></${State}>
    <${State} name="the open task, its session"><${Pane} card=${cards[1].props} tab="session"
      shown=${{ held: true, shown: "› npm test\n  ✗ restores the stored mode on load" }}/></${State}>
    <${State} name="the open task, its session has no window"><${Pane} card=${cards[1].props} tab="session"
      shown=${{ held: false, shown: "" }}/></${State}>
    <${State} name="the open task, its session shows a blank screen"><${Pane} card=${cards[1].props} tab="session"
      shown=${{ held: true, shown: "" }}/></${State}>
    <${State} name="a round drafted: two blocks, a quote, the three verdicts"><${Pane} card=${{
      ...cards[1].props,
      offers: [{ id: "replies", hint: "the drafted answers", part: "round" }],
    }} tab="round" shown=${DRAFTED}/></${State}>
    <${State} name="the open task, a round not drafted"><${Pane} card=${cards[1].props} tab="round" shown=${null}/></${State}>
  </main>
</section>`;
