import "./ui/translate.js";
const failed = document.getElementById("failed");
if (failed) failed.hidden = !location.search.includes("error");
