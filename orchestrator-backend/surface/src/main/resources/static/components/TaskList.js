// Every task as one row, the human's own first: a row moves only when whose move it is changes. Only the open
// row is a Tab stop; the keys walk the rest, so Tab reaches the open task past one row and not past all of them.

import {duration} from '../core/format.js';
import {html} from './html.js';
import {marked, watchLine} from './Meta.js';

const quickClass = (action) => (action.primary ? 'row-verb primary' : 'row-verb');

const Row = ({task, selected, changed, project, now}) => {
  const owner = task.owner.toLowerCase();
  const stopped = watchLine(task.autoReview, now)?.stalled ? watchLine(task.autoReview, now).line : null;
  const quick = (task.actions || []).find((action) => action.onRow);
  return html`<div class=${['row', owner, task.attention === 'OPTIONAL' && 'optional', selected && 'selected']
      .filter(Boolean).join(' ')} data-row=${task.id}>
    <button class="pick" data-select=${task.id} aria-current=${selected ? 'true' : undefined} tabIndex=${selected ? 0 : -1}
        aria-label=${[`${task.alias || task.id} ${task.title || ''}`, task.statusLabel,
          marked(task) && `checks: ${task.pipelineSaid || task.pipeline.toLowerCase()}`,
          stopped, changed && 'changed since you looked'].filter(Boolean).join(', ')}>
      <span class="row-line">
        <span class="alias">${task.alias || '-'}</span>
        <span class=${changed ? 'row-title changed' : 'row-title'}>${task.title || task.id}</span>
        ${task.ask ? html`<span class=${`badge ${task.attention.toLowerCase()}${task.detailKind === 'PROBLEM' ? ' problem' : ''}`}>${task.ask}</span>` : ''}
      </span>
      <span class="row-line row-facts">
        <span class=${stopped ? 'status stalled' : 'status'} data-tip=${stopped || undefined}
          >${task.statusLabel} <span class="age">${duration(now - task.statusSince)}</span></span>
        ${project ? html`<span>${task.project}</span>` : ''}
        ${marked(task) ? html`<span class=${`checks ${task.pipeline.toLowerCase()}`} aria-hidden="true"></span>` : ''}
      </span>
    </button>
    ${quick ? html`<button class=${quickClass(quick)} data-task=${task.id} data-action=${quick.id} data-place="row"
        tabIndex=${selected ? 0 : -1}
        aria-label=${`${quick.label} ${task.alias || task.id}`} data-tip=${quick.hint}><svg width="16" height="16"
        viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"
        stroke-linejoin="round" aria-hidden="true"><rect x="3" y="4" width="18" height="16" rx="2"></rect>
        <path d="M7 9l3 3-3 3M13 15h4"></path></svg></button>` : ''}
  </div>`;
};

const Group = ({name, kind, tasks, named, selected, changed, now}) => html`<section class=${`group ${kind}`} aria-label=${name}>
  ${name ? html`<h2 class="group-head">${name} <span class="count">${tasks.length}</span></h2>` : ''}
  ${tasks.map((task) => html`<${Row} key=${task.id} task=${task} selected=${task.id === selected}
    changed=${changed(task)} project=${named} now=${now}/>`)}
</section>`;

// The rows in the order the keyboard walks them: the human's band first, then each project in its first-seen order;
// a flagged row leads its group.
export function grouped(shown, manyProjects, flagged = () => false) {
  const leading = shown.filter(flagged);
  const ordered = [...leading, ...shown.filter((task) => !leading.includes(task))];
  const yours = ordered.filter((task) => task.attention === 'REQUIRED');
  const rest = ordered.filter((task) => task.attention !== 'REQUIRED');
  const projects = [...new Set(rest.map((task) => task.project))];
  const groups = manyProjects
    ? projects.map((project) => ({name: project, kind: 'project', tasks: rest.filter((task) => task.project === project)}))
    : [{name: yours.length ? 'the rest' : '', kind: 'project', tasks: rest}];
  return [...(yours.length ? [{name: 'yours', kind: 'yours', tasks: yours, named: manyProjects}] : []), ...groups]
    .filter((group) => group.tasks.length);
}

export const TaskList = ({groups, selected, changed, now, keys}) => html`<nav id="tasks" aria-label="tasks">${
  groups.map((group) => html`<${Group} key=${group.kind + group.name} ...${group} selected=${selected}
    changed=${changed} now=${now}/>`)}${keys ? html`<p class="keys">${keys}</p>` : ''}</nav>`;
