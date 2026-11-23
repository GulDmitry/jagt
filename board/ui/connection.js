const STALE = [
  "board",
  "phases",
  "new-task",
  "empty-new",
  "palette",
  "open-palette",
  "reports-toggle",
  "report-line",
  "role-chips",
  "role-logs",
  "jobs-pulse",
  "launch",
  "project-form",
  "empty-project",
];

/** @param {boolean} yes */
export const connected = (yes) => {
  /** @type {HTMLElement} */ (document.getElementById("offline")).hidden = yes;
  document.body.classList.toggle("stale", !yes);
  STALE.forEach((id) => {
    /** @type {HTMLElement} */ (document.getElementById(id)).inert = !yes;
  });
};
