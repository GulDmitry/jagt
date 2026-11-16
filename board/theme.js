try {
  document.documentElement.dataset.theme = localStorage.theme || "";
  document.documentElement.lang = localStorage.lang || "en";
} catch {
  // storage blocked
}
