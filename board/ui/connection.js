// Cut off from the backend, the board is a picture of the past: nothing on it may act.
const STALE = [
  "board",
  "phases",
  "new-task",
  "empty-new",
  "palette",
  "open-palette",
  "reports-toggle",
  "report-line",
  "act-stopped",
  "jobs-pulse",
  "launch",
  "project-form",
  "empty-project",
];

export const connected = (yes) => {
  document.getElementById("offline").hidden = yes;
  document.body.classList.toggle("stale", !yes);
  STALE.forEach((id) => {
    document.getElementById(id).inert = !yes;
  });
};
