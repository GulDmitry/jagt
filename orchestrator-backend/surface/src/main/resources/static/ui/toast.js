// A toast is gone in seconds, so every one is kept here for the session; the log file keeps what matters longer.

const toasts = document.getElementById('toasts');
const opener = document.getElementById('show-log');
const messages = [];

export const sessionLog = () => messages.join('\n');

// The dialog is not this module's to own, so the click is handed over.
export const showLog = (open) => { opener.onclick = open; };

export function toast(message, isError) {
  messages.push(`${new Date().toLocaleTimeString()}  ${message}`);
  opener.hidden = false;
  opener.dataset.tip = `every message this session; the last:\n${message}`;

  // An error interrupts a screen reader; the rest wait their turn in the status region.
  const node = document.createElement('div');
  if (isError) node.setAttribute('role', 'alert');
  const dismiss = document.createElement('button');
  dismiss.type = 'button';
  dismiss.className = isError ? 'toast error' : 'toast';
  dismiss.textContent = message;
  dismiss.onclick = () => node.remove();
  node.append(dismiss);
  toasts.append(node);
  setTimeout(() => node.remove(), isError ? 12000 : 7000);
}
