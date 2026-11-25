import { t } from "../core/i18n.js";
import { showReport } from "./dialogs.js";
import { sessionLog, showLog } from "./toast.js";

showLog(() => showReport(t("log.title"), sessionLog(), null, true));
