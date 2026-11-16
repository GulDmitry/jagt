import { onVerbClick } from "./chips.js";
import { refresh } from "./refresh.js";
import { onSent } from "./submit.js";
import { runGlobal } from "./verbs.js";

onSent(refresh);
onVerbClick(runGlobal);
