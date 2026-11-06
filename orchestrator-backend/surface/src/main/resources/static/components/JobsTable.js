// Every job as a row, grouped by whom it serves, a failed one first; its words and groups are the server's.

import {countdown} from '../core/format.js';
import {t} from '../core/i18n.js';
import {html} from './html.js';

const ago = (at, now) => (at ? t('jobs.ago', {when: countdown(now - at)}) : t('jobs.not-yet'));

const dot = (job) => (job.lastError ? 'red' : job.running ? 'running' : job.lastStartedAt ? 'green' : '');

const next = (job, now) => (job.nextRunAt ? (job.nextRunAt <= now ? t('due') : t('jobs.in', {when: countdown(job.nextRunAt - now)}))
  : t(job.lastStartedAt ? 'jobs.not-again' : 'jobs.at-start'));

const Row = ({job, now}) => html`<tr class=${job.lastError ? 'failed' : undefined} data-tip=${t('jobs.next', {when: next(job, now)})}>
  <td><span class=${`checks ${dot(job)}`} role="img"
    aria-label=${t(job.lastError ? 'jobs.failed' : job.running ? 'jobs.running' : job.lastStartedAt ? 'jobs.fine' : 'jobs.never')}></span></td>
  <td class="job-id">${job.id}</td>
  <td>${job.describe}${job.lastError ? html`<div class="detail problem">${job.lastError}</div>` : ''}</td>
  <td class=${job.lastError ? 'last failed' : 'muted'}>${job.running ? t('jobs.running') : ago(job.lastStartedAt, now)}</td>
  <td><button type="button" class="run-now" data-job=${job.id} disabled=${job.running}>${t('jobs.run-now')}</button></td>
</tr>`;

export const JobsTable = ({jobs, now}) => html`${[...new Set(jobs.map((job) => job.serves))].map((group) => html`
  <table class="jobs" aria-label=${group}>
    <caption class="group-head">${group}</caption>
    <thead><tr><th></th><th>${t('jobs.col.job')}</th><th>${t('jobs.col.does')}</th><th>${t('jobs.col.last')}</th><th></th></tr></thead>
    <tbody>${jobs.filter((job) => job.serves === group)
      .sort((one, other) => Number(Boolean(other.lastError)) - Number(Boolean(one.lastError)))
      .map((job) => html`<${Row} key=${job.id} job=${job} now=${now}/>`)}</tbody>
  </table>`)}`;
