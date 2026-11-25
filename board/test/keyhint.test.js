import assert from "node:assert/strict";
import { test } from "node:test";
import { said } from "../ui/keyhint.js";

test("hintListsOnlyTheFixedKeys whenTheRowHasNoVerb", () => {
  assert.equal(
    said(undefined),
    "keys.walk · keys.open · keys.next · keys.new · keys.filter · keys.command",
  );
});

test("hintNamesTheRowVerbKey whenTheRowOffersOne", () => {
  const verb = /** @type {never} */ ({ key: "d", label: "Deploy" });

  assert.equal(
    said(verb),
    "keys.walk · keys.open · keys.next · d deploy · keys.new · keys.filter · keys.command",
  );
});

test("hintSkipsTheRowVerb whenItHasNoKey", () => {
  const verb = /** @type {never} */ ({ label: "Deploy" });

  assert.equal(
    said(verb),
    "keys.walk · keys.open · keys.next · keys.new · keys.filter · keys.command",
  );
});
