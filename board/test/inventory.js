import { readdirSync, readFileSync } from "node:fs";
import { argv, stdout } from "node:process";
import { fileURLToPath } from "node:url";

const root = new URL("../", import.meta.url);
const docs = new URL("../docs/rules/", root);

/** @param {string} file */
const read = (file) => readFileSync(new URL(file, root), "utf8");

const css = () => read("app.css").replace(/\/\*[\s\S]*?\*\//g, "");

const selectors = () =>
  [...css().matchAll(/([^{};]+)\{/g)]
    .map(([, prelude]) => prelude.trim())
    .filter((prelude) => !/^(@|from\b|to\b|\d)/.test(prelude));

export const classes = () => [
  ...new Set(
    selectors().flatMap((selector) =>
      [...selector.matchAll(/\.(-?[A-Za-z_][\w-]*)/g)].map(([, name]) => name),
    ),
  ),
];

export const tokens = () => [
  ...new Set([...css().matchAll(/(--[\w-]+)\s*:/g)].map(([, name]) => name)),
];

const SKIPPED =
  /^(node_modules|vendor|test|coverage|coverage-floors|styleguide)(\/|$)|^styleguide\.html$|\.config\.m?js$/;

/** @param {RegExp} kind */
const boardFiles = (kind) =>
  readdirSync(root, { recursive: true, encoding: "utf8" })
    .filter((file) => kind.test(file) && !SKIPPED.test(file))
    .sort();

export const boardSources = () => boardFiles(/\.(m?js|html)$/);

export const stylesheets = () => boardFiles(/\.css$/);

export const styleBookSources = () =>
  readdirSync(new URL("styleguide/", root), { encoding: "utf8" })
    .filter((file) => file.endsWith(".js"))
    .map((file) => `styleguide/${file}`)
    .sort();

export const components = () =>
  readdirSync(new URL("components/", root), { encoding: "utf8" })
    .filter((file) => /^[A-Z]\w*\.js$/.test(file))
    .map((file) => `components/${file}`)
    .sort();

/** @param {string} file @returns {string[]} */
export const importsOf = (file) =>
  [
    ...read(file).matchAll(/(?:from\s+|import\s*\(?\s*)"(\.{1,2}\/[^"]+)"/g),
  ].map(([, path]) =>
    new URL(path, new URL(file, root)).pathname.slice(root.pathname.length),
  );

/** @param {string} file @param {Set<string>} [seen] @returns {Set<string>} */
export const reachedFrom = (file, seen = new Set()) => {
  if (seen.has(file)) return seen;
  seen.add(file);
  importsOf(file).forEach((each) => reachedFrom(each, seen));
  return seen;
};

/** @param {string[]} files */
export const text = (files) => files.map(read).join("\n");

export const designFiles = () =>
  readdirSync(docs, { encoding: "utf8" })
    .filter((file) => /^design(-[\w-]+)?\.md$/.test(file))
    .sort();

export const design = () =>
  designFiles()
    .map((file) => readFileSync(new URL(file, docs), "utf8"))
    .join("\n");

if (argv[1] === fileURLToPath(import.meta.url)) {
  stdout.write(
    `${JSON.stringify({ classes: classes(), tokens: tokens(), components: components() }, null, 2)}\n`,
  );
}
