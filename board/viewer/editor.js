import { reveal, show, showChange } from "./codemirror.js";
import { original } from "./patch.js";

export { reveal };

/** Shows the file in `host`, its change marked when `picked` is one; resolves to what closes it. */
export const open = (host, file, picked) =>
  picked.change
    ? showChange(
        host,
        original(file.text, picked.change.hunks),
        file.text,
        file.path,
      )
    : show(host, file.text, file.path);
