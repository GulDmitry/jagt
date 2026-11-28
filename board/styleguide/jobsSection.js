import { html } from "../components/html.js";
import { JobsTable } from "../components/JobsTable.js";
import { ProjectChecks } from "../components/ProjectChecks.js";
import { State } from "./State.js";

export const jobsSection = html`<section aria-label="Jobs">
  <h2>Jobs</h2>
  <main><${State} name="one failed, one never run, one running, one whose last run passed"><div class="jobs-table"><${JobsTable} now=${Date.now()} jobs=${[
    {
      id: "verify",
      describe: "runs the project’s verify command",
      serves: "your tasks",
      lastStartedAt: Date.now() - 360000,
      lastError: "exit 1: 2 tests fail",
      nextRunAt: Date.now() + 14000,
      running: false,
    },
    {
      id: "intake",
      describe: "starts a task on every item marked ready",
      serves: "the tracker",
      lastStartedAt: null,
      lastError: null,
      nextRunAt: Date.now() + 540000,
      running: false,
    },
    {
      id: "usage",
      describe: "reads the subscription’s limits",
      serves: "housekeeping",
      lastStartedAt: Date.now(),
      lastError: null,
      nextRunAt: null,
      running: true,
    },
    {
      id: "cleanup",
      describe: "removes the worktrees of closed tasks",
      serves: "housekeeping",
      lastStartedAt: Date.now() - 600000,
      lastError: null,
      nextRunAt: Date.now() + 3000000,
      running: false,
    },
  ]}/></div></${State}></main>
</section>`;

export const checksSection = html`<section aria-label="Project checks">
  <h2>Project checks</h2>
  <main><${State} name="a repository ready to add"><ul class="checks-list"><${ProjectChecks} lines=${[
    { ok: true, text: "a git repository, origin git@host:g/billing.git" },
    { ok: true, text: "tasks are cut from origin/main" },
    { ok: false, text: "the key billing is taken: pick another" },
  ]}/></ul></${State}></main>
</section>`;
