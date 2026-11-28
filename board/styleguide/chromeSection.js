import { html } from "../components/html.js";
import { State } from "./State.js";

const MAGNIFIER = html`<svg
  width="14"
  height="14"
  viewBox="0 0 24 24"
  fill="none"
  stroke="currentColor"
  stroke-width="2"
  aria-hidden="true"
>
  <circle cx="11" cy="11" r="7" />
  <path d="M20 20l-3.5-3.5" />
</svg>`;

export const chromeSection = html`<section aria-label="Chrome">
  <h2>Chrome</h2>
  <main>
    <${State} name="the header: role chips, a spacer, the filter box, a menu"><header>
      <span class="role-chips"><button class="chip" type="button">reviewer</button></span>
      <span class="grow"></span>
      <label class="search">${MAGNIFIER}<input placeholder="filter" /><button type="button">⌘K</button></label>
      <div class="menu"><button type="button" aria-expanded="false">Reports</button></div>
    </header></${State}>
    <${State} name="the phase bar: a filter pressed, one at zero, a sort, a reset"><div id="phases">
      <button class="phase" aria-pressed="true">Plan <span class="count">2</span></button>
      <span class="sep"> · </span>
      <button class="phase empty" disabled>Review <span class="count">0</span></button>
      <span class="sep"> · </span>
      <button class="order">by alias</button>
      <button class="clear-filters">clear 1</button>
    </div></${State}>
    <${State} name="no task yet, and the board open under 1200px"><section class="empty">
      <p>No task yet.</p>
    </section><main id="board" class="open"><p class="muted">the pane replaces the list</p></main></${State}>
    <${State} name="a dialog: title bar, two fields side by side, a checkbox, a default note, the foot"><form>
      <header class="report-head"><strong>New task</strong><span class="grow"></span><button type="button">close</button></header>
      <label class="field"><span>Ticket</span><input placeholder="ABC-42" /><span class="hint">default</span></label>
      <div class="field-row">
        <label class="field"><span>Base</span><input placeholder="main" /></label>
        <label class="checkbox"><input type="checkbox" /> <span>plan first</span></label>
      </div>
      <footer class="dialog-foot"><span class="muted state">ready</span><span class="grow"></span>
        <button type="submit" class="primary">Start</button></footer>
    </form></${State}>
    <${State} name="a hover taller than the screen takes the pointer to scroll"><div id="tip" class="scrolls">A long hover
line two
line three</div></${State}>
    <${State} name="the login page's form"><form class="login">
      <input type="password" placeholder="password" />
      <button class="primary" type="button">Sign in</button>
    </form></${State}>
  </main>
</section>`;
