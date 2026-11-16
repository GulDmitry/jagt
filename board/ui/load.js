import * as menu from "./menu.js";
import { refresh, refreshVerbs } from "./refresh.js";
import { render } from "./render.js";
import { judge, refreshSuggestions } from "./verdict.js";

export async function loadVerbs() {
  await refreshVerbs();
  refreshSuggestions();
  judge();
  menu.render();
  render();
}

// A line typed while the board was still being read is judged again against what arrived.
export const readBoard = () => refresh().then(judge);
