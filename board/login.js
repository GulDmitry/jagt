import "./core/i18n.js";
const failed = document.getElementById("failed");
if (failed) failed.hidden = !location.search.includes("error");
