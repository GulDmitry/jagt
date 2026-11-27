import { t } from "../core/i18n.js";
import { html } from "../components/html.js";
import { Names } from "./names.js";
import { useCode } from "./useCode.js";

/** @type {Record<string, string>} */
const SAID = { BINARY: "code.binary", TOO_LARGE: "code.large" };

/** @param {{ task: { id: string } }} props */
export const Code = ({ task }) => {
  const {
    trees,
    picked,
    setPicked,
    file,
    found,
    setFound,
    mode,
    setMode,
    changes,
    failure,
    host,
    search,
  } = useCode(task);
  const changed = mode === "changes";
  return html`<section
    class="code"
    role="tabpanel"
    id="panel-code"
    aria-labelledby="tab-code"
  >
    <form class="code-search" onSubmit=${search}>
      <input
        name="q"
        aria-label=${t("code.search")}
        placeholder=${t("code.search")}
        autocomplete="off"
      />
      <span class="code-modes"
        >${["files", "changes"].map(
          (one) =>
            html`<button
              type="button"
              onClick=${() => {
                setMode(one);
                setFound(null);
              }}
              aria-pressed=${String(mode === one)}
            >
              ${t(`code.${one}`)}
            </button>`,
        )}</span
      >
    </form>
    ${failure ? html`<p class="detail problem">${failure}</p>` : ""}
    <div class="code-body">
      <div class="code-names">
        <${Names}
          found=${found}
          changed=${changed}
          changes=${changes}
          trees=${trees}
          picked=${picked}
          failure=${failure}
          pick=${setPicked}
        />
        ${found?.cut ? html`<p class="muted">${t("code.cut")}</p>` : ""}
      </div>
      <div class="code-file">
        ${file && file.kind !== "TEXT" ? html`<p class="muted">${t(SAID[file.kind])}</p>` : ""}
        <div class="code-view" ref=${host}></div>
      </div>
    </div>
  </section>`;
};
