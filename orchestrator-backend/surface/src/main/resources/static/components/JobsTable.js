// Every job as a row, grouped by whom it serves, a failed one first; its words and groups are the server's.

import {countdown} from '../core/format.js';
import {html} from './html.js';

const ago = (at, now) => (at ? `${countdown(now - at)} ago` : 'not yet');

const dot = (job) => (job.lastError ? 'red' : job.running ? 'running' : job.lastStartedAt ? 'green' : '');

const Row = ({job, now}) => html`<tr class=${job.lastError ? 'failed' : undefined}>
  <td><span class=${`checks ${dot(job)}`} role="img"
    aria-label=${job.lastError ? 'last run failed' : job.running ? 'running now' : job.lastStartedAt ? 'last run fine' : 'not run yet'}></span></td>
  <td class="job-id">${job.id}</td>
  <td>${job.describe}${job.lastError ? html`<div class="detail problem">${job.lastError}</div>` : ''}</td>
  <td class=${job.lastError ? 'last failed' : 'muted'}>${job.running ? 'running now' : ago(job.lastStartedAt, now)}</td>
  <td class="muted">${job.nextRunAt ? (job.nextRunAt <= now ? 'due' : countdown(job.nextRunAt - now))
    : job.lastStartedAt ? 'not again' : 'at start'}</td>
  <td><button type="button" class="run-now" data-job=${job.id} disabled=${job.running}>Run now</button></td>
</tr>`;

export const JobsTable = ({jobs, now}) => html`${[...new Set(jobs.map((job) => job.serves))].map((group) => html`
  <table class="jobs" aria-label=${group}>
    <caption class="group-head">${group}</caption>
    <thead><tr><th></th><th>job</th><th>what it does</th><th>last run</th><th>next</th><th></th></tr></thead>
    <tbody>${jobs.filter((job) => job.serves === group)
      .sort((one, other) => Number(Boolean(other.lastError)) - Number(Boolean(one.lastError)))
      .map((job) => html`<${Row} key=${job.id} job=${job} now=${now}/>`)}</tbody>
  </table>`)}`;
