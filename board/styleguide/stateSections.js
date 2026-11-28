import { cardFrames } from "./cardSection.js";
import { statusCards } from "./statuses.js";
import { stopCards } from "./stops.js";

export const statusSection = cardFrames("Every status", statusCards);
export const stopSection = cardFrames("Every stop", stopCards);
