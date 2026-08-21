// A toast is gone in seconds, so every one is also kept here for the session. No persistence: the backend's
// own log file keeps what matters longer.

const toasts = document.getElementById('toasts');
const opener = document.getElementById('show-log');
const messages = [];

export const sessionLog = () => messages.join('\n');

// What the log button OPENS is a dialog, which this module does not own: it hands over the click instead.
export const showLog = (open) => { opener.onclick = open; };

export function toast(message, isError) {
  messages.push(`${new Date().toLocaleTimeString()}  ${message}`);
  opener.hidden = false;
  opener.textContent = message.split('\n')[0];

  const node = document.createElement('div');
  node.className = isError ? 'toast error' : 'toast';
  node.textContent = message;
  node.onclick = () => node.remove();
  toasts.append(node);
  setTimeout(() => node.remove(), isError ? 12000 : 7000);
}
