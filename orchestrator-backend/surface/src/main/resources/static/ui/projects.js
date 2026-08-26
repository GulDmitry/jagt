// The project picker of the launch form, filled from the same snapshot the board renders.

import * as store from '../core/store.js';

const select = document.getElementById('project');
// An untouched default is not a decision, and naming a project SKIPS the ticket read — the escape hatch a
// typed `do ABC-1 <project>` deliberately takes. So the list opens on a real project, and only a pick is sent.
let picked = false;
let rendered = null;
select.onchange = () => { picked = true; };

// The order they were picked in does not survive a multi-select, so the CONFIGURED order decides which
// repository the agent's session runs in.
export const chosen = () => (picked
  ? [...select.selectedOptions].map((option) => option.value).join(',')
  : '');

export const forget = () => { picked = false; };

export function render() {
  const configured = store.projects();
  const signature = configured.join('\n');
  if (rendered === signature) {
    return;                       // a rebuild collapses the list under a human who has it open
  }
  rendered = signature;
  const chosenBefore = select.value;
  select.replaceChildren(...(configured.length
    ? configured.map((project) => new Option(project, project))
    : [Object.assign(new Option('no projects in jagt.yml', ''), {disabled: true})]));
  if (configured.includes(chosenBefore)) {
    select.value = chosenBefore;
  } else {
    picked = false;
  }
}
