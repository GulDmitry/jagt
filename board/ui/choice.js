import { make } from "../core/dom.js";

const RADIOS_AT_MOST = 4;

/**
 * @param {string} name
 * @param {{ value: string, label: string }[]} options
 * @param {string} selected
 * @param {(value: string) => void} picked
 */
export const choice = (name, options, selected, picked) => {
  if (options.length > RADIOS_AT_MOST) {
    const select = Object.assign(document.createElement("select"), { name });
    select.append(
      ...options.map(({ value, label }) =>
        Object.assign(document.createElement("option"), {
          value,
          textContent: label,
          selected: value === selected,
        }),
      ),
    );
    select.onchange = () => picked(select.value);
    return select;
  }
  const group = make("div", "choice");
  group.setAttribute("role", "radiogroup");
  group.append(
    ...options.map(({ value, label }) =>
      make(
        "label",
        "",
        "",
        Object.assign(document.createElement("input"), {
          type: "radio",
          name,
          value,
          checked: value === selected,
        }),
        make("span", "", label),
      ),
    ),
  );
  const mark = () =>
    group
      .querySelectorAll("input")
      .forEach((radio) =>
        radio.setAttribute("aria-checked", String(radio.checked)),
      );
  group.onchange = ({ target }) => {
    mark();
    if (target instanceof HTMLInputElement) picked(target.value);
  };
  mark();
  return group;
};
