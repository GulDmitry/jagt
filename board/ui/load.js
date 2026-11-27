import * as menu from "./menu.js";
import { refresh } from "./refresh.js";
import { refreshVerbs } from "./refreshverbs.js";
import { render } from "./render.js";
import { judge, refreshSuggestions } from "./verdict.js";

export async function loadVerbs() {
  await refreshVerbs();
  refreshSuggestions();
  judge();
  menu.render();
  render();
}

export const readBoard = () => refresh().then(judge);
