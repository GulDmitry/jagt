// Every job as a row, grouped by whom it serves; its words and groups are the server's.

import {countdown} from '../core/format.js';
import {html} from './html.js';

const ago = (at, now) => (at ? `${countdown(now - at)} ago` : 'not yet');

const dot = (job) => (job.lastError ? 'red' : job.running ? 'running' : job.lastStartedAt ? 'green' : '');

const Row = ({job, now}) => html`<tr class=${job.lastError ? 'failed' : undefined}>
  <td><span class=${`checks ${dot(job)}`} role="img"
    aria-label=${job.lastError ? 'last run failed' : job.running ? 'running now' : 'last run fine'}></span></td>
  <td class="job-id">${job.id}</td>
  <td>${job.describe}${job.lastError ? html`<div class="detail problem">${job.lastError}</div>` : ''}</td>
  <td class="muted">${job.running ? 'running now' : ago(job.lastStartedAt, now)}</td>
  <td class="muted" data-tip="next run">${job.nextRunAt ? countdown(Math.max(0, job.nextRunAt - now)) : 'at start'}</td>
  <td><button type="button" class="run-now" data-job=${job.id} disabled=${job.running}>Run now</button></td>
</tr>`;

export const JobsTable = ({jobs, now}) => html`${[...new Set(jobs.map((job) => job.serves))].map((group) => html`
  <table class="jobs" aria-label=${group}>
    <caption class="group-head">${group}</caption>
    <tbody>${jobs.filter((job) => job.serves === group).map((job) => html`<${Row} key=${job.id} job=${job} now=${now}/>`)}</tbody>
  </table>`)}`;
