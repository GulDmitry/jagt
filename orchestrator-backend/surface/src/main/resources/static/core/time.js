// Every time the board shows is UTC and English, whatever the browser's own zone or language.

const format = (options) => new Intl.DateTimeFormat('en-GB', {timeZone: 'UTC', hourCycle: 'h23', ...options});

const SECONDS = format({hour: '2-digit', minute: '2-digit', second: '2-digit'});
const MINUTES = format({hour: '2-digit', minute: '2-digit'});
const DAY_AND_MINUTES = format({day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit'});
const FULL = format({day: 'numeric', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit'});

export const withSeconds = (at) => SECONDS.format(at);
export const minutes = (at) => MINUTES.format(at);
export const dayAndMinutes = (at) => DAY_AND_MINUTES.format(at);
export const full = (at) => `${FULL.format(at)} UTC`;
export const sameDay = (a, b) => new Date(a).toISOString().slice(0, 10) === new Date(b).toISOString().slice(0, 10);
