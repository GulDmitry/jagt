import assert from "node:assert/strict";
import { test } from "node:test";
import {
  boardSources,
  classes,
  components,
  design,
  reachedFrom,
  stylesheets,
  text,
  tokens,
} from "./inventory.js";

const COMMENT = /\/\*[\s\S]*?\*\/|<!--[\s\S]*?-->|(?<=^|\s)\/\/.*$/gm;

test("everyClassIsUsedByTheBoard whenAppCssStylesIt", () => {
  const quoted = new Set(
    boardSources()
      .map((file) => text([file]).replace(COMMENT, ""))
      .flatMap((source) => [
        ...source.matchAll(/"([^"\n]*)"/g),
        ...source.replace(/"[^"\n]*"/g, "").matchAll(/`([^`]*)`/g),
      ])
      .flatMap(([, literal]) => literal.split(/[^\w-]+/)),
  );

  const unused = classes().filter((name) => !quoted.has(name));

  assert.deepEqual(unused, []);
});

test("everyTokenIsReadByAStyle whenAppCssDefinesIt", () => {
  const styles = text([...stylesheets(), ...boardSources()]);

  const unread = tokens().filter((name) => !styles.includes(`var(${name})`));

  assert.deepEqual(unread, []);
});

test("everyClassIsDescribedInTheDesignCode whenAppCssStylesIt", () => {
  const described = design();

  const undescribed = classes().filter(
    (name) => !new RegExp(`\\.${name}(?![\\w-])`).test(described),
  );

  assert.deepEqual(undescribed, []);
});

test("everyTokenIsDescribedInTheDesignCode whenAppCssDefinesIt", () => {
  const described = design();

  const undescribed = tokens().filter(
    (name) => !new RegExp(`${name}(?![\\w-])`).test(described),
  );

  assert.deepEqual(undescribed, []);
});

test("everyComponentIsDrawnInTheStyleBook whenTheBoardHasIt", () => {
  const drawn = reachedFrom("styleguide/index.js");

  const undrawn = components().filter((file) => !drawn.has(file));

  assert.deepEqual(undrawn, []);
});
