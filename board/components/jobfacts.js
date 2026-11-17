import { countdown } from "../core/format.js";
import { t } from "../core/i18n.js";

/**
 * @typedef {{ id: string, serves: string, describe: string, running?: boolean, lastError?: string | null, lastStartedAt?: number | null, nextRunAt?: number | null }} Job
 */

/**
 * @param {number | null | undefined} at
 * @param {number} now
 */
export const ago = (at, now) =>
  at ? t("jobs.ago", { when: countdown(now - at) }) : t("jobs.not-yet");

/** @param {Job} job */
export const dot = (job) =>
  job.lastError
    ? "red"
    : job.running
      ? "running"
      : job.lastStartedAt
        ? "green"
        : "";

/** @param {Job} job */
export const verdict = (job) =>
  t(
    job.lastError
      ? "jobs.failed"
      : job.running
        ? "jobs.running"
        : job.lastStartedAt
          ? "jobs.fine"
          : "jobs.never",
  );

/**
 * @param {Job} job
 * @param {number} now
 */
export const next = (job, now) =>
  job.nextRunAt
    ? job.nextRunAt <= now
      ? t("due")
      : t("jobs.in", { when: countdown(job.nextRunAt - now) })
    : t(job.lastStartedAt ? "jobs.not-again" : "jobs.at-start");

/** @param {Job[]} jobs */
export const tables = (jobs) =>
  [...new Set(jobs.map((job) => job.serves))].map((group) => ({
    group,
    jobs: jobs
      .filter((job) => job.serves === group)
      .sort(
        (one, other) =>
          Number(Boolean(other.lastError)) - Number(Boolean(one.lastError)),
      ),
  }));
