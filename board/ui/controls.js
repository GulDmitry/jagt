import { run } from "./act.js";
import { onClick } from "./clicks.js";
import * as filters from "./filters.js";
import { onBarClick } from "./phasebar.js";
import { render } from "./render.js";

onClick({ action: run, repaint: render });
onBarClick(render);
filters.onChange(render);
setInterval(render, 15000);
