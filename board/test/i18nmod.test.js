import assert from "node:assert/strict";
import { afterEach, mock, test } from "node:test";

const loads = { count: 0 };
/** @returns {Promise<typeof import("../core/i18n.js")>} */
const fresh = () => import(`../core/i18n.js?mod=${++loads.count}`);

afterEach(() => mock.restoreAll());

test("theModifierIsCtrl whenTheBrowserIsNotApple", async () => {
  mock.method(
    globalThis,
    "fetch",
    async (/** @type {string} */ url) =>
      new Response(url === "i18n/en.json" ? '{"chord":"{mod}K"}' : "", {
        status: url === "i18n/en.json" ? 200 : 404,
      }),
  );
  const { t } = await fresh();

  assert.equal(t("chord"), "Ctrl+K");
});

test("theModifierIsCommand whenTheBrowserIsApple", async () => {
  mock.method(
    globalThis,
    "fetch",
    async (/** @type {string} */ url) =>
      new Response(url === "i18n/en.json" ? '{"chord":"{mod}K"}' : "", {
        status: url === "i18n/en.json" ? 200 : 404,
      }),
  );
  Object.defineProperty(globalThis, "navigator", {
    value: { userAgent: "Mac" },
    configurable: true,
  });
  const { t } = await fresh();
  Reflect.deleteProperty(globalThis, "navigator");

  assert.equal(t("chord"), "⌘K");
});
