import assert from "node:assert/strict";
import { test } from "node:test";
import { files, original } from "../viewer/patch.js";

const MODIFIED = [
  "diff --git a/src/a.txt b/src/a.txt",
  "index 111..222 100644",
  "--- a/src/a.txt",
  "+++ b/src/a.txt",
  "@@ -1,3 +1,3 @@",
  " one",
  "-two",
  "+TWO",
  " three",
].join("\n");

test("noFiles whenTheDiffIsEmpty", () => {
  assert.deepEqual(files(""), []);
});

test("linesBeforeTheFirstHeaderAreIgnored whenTheDiffHasAPreamble", () => {
  assert.equal(files(`noise\n${MODIFIED}`).length, 1);
});

test("pathIsTheTargetPath whenAFileIsModified", () => {
  assert.equal(files(MODIFIED)[0].path, "src/a.txt");
});

test("addedAndRemovedAreCounted whenAHunkChangesLines", () => {
  const [one] = files(MODIFIED);

  assert.deepEqual([one.added, one.removed], [1, 1]);
});

test("hunkKeepsItsStartCountAndLines whenAHunkIsRead", () => {
  const [one] = files(MODIFIED);

  assert.deepEqual(one.hunks, [
    { start: 1, count: 3, lines: [" one", "-two", "+TWO", " three"] },
  ]);
});

test("hunkCountIsOne whenTheHeaderOmitsIt", () => {
  const diff = "diff --git a/f b/f\n--- a/f\n+++ b/f\n@@ -2 +2 @@\n-x\n+y";

  assert.equal(files(diff)[0].hunks[0].count, 1);
});

test("fileIsGone whenTheTargetIsDevNull", () => {
  const diff = "diff --git a/f b/f\n--- a/f\n+++ /dev/null\n@@ -1 +0,0 @@\n-x";

  const [one] = files(diff);

  assert.deepEqual([one.gone, one.path, one.removed], [true, "f", 1]);
});

test("everyFileIsListed whenTheDiffNamesTwo", () => {
  const diff = `${MODIFIED}\ndiff --git a/b b/b\n--- /dev/null\n+++ b/b\n@@ -0,0 +1 @@\n+new`;

  assert.deepEqual(
    files(diff).map((one) => [one.path, one.added]),
    [
      ["src/a.txt", 1],
      ["b", 1],
    ],
  );
});

test("plusPlusPlusLineInsideAHunkIsContent whenItStartsWithPlusPlusPlusSpace", () => {
  const diff = "diff --git a/f b/f\n--- a/f\n+++ b/f\n@@ -0,0 +1 @@\n+++ text";

  assert.equal(files(diff)[0].added, 1);
});

test("textBeforeIsRebuilt whenAHunkReplacesALine", () => {
  const [one] = files(MODIFIED);

  assert.equal(original("one\nTWO\nthree", one.hunks), "one\ntwo\nthree");
});

test("textBeforeKeepsLinesBetweenHunks whenTwoHunksChange", () => {
  const hunks = [
    { start: 1, count: 1, lines: ["-a", "+A"] },
    { start: 4, count: 1, lines: ["-d", "+D"] },
  ];

  assert.equal(original("A\nb\nc\nD\ne", hunks), "a\nb\nc\nd\ne");
});

test("textBeforeLacksTheAddedLines whenAHunkOnlyAdds", () => {
  const hunks = [{ start: 1, count: 1, lines: ["+x"] }];

  assert.equal(original("x\nrest", hunks), "rest");
});

test("textBeforeHasTheDeletedLine whenAHunkOnlyDeletes", () => {
  const hunks = [{ start: 1, count: 0, lines: ["-y"] }];

  assert.equal(original("a\nb", hunks), "a\ny\nb");
});

const WITH_LITERAL_MARKERS = [
  "diff --git a/n.txt b/n.txt",
  "--- a/n.txt",
  "+++ b/n.txt",
  "@@ -10,20 +30,40 @@",
  " keep",
  "+@@ -1 +1 @@",
  "+diff --git a/x b/x",
  "\\ No newline at end of file",
].join("\n");

test("markerLookingContentIsContent whenAnAddedLineQuotesAHunkOrFileHeader", () => {
  const [one, ...others] = files(WITH_LITERAL_MARKERS);

  assert.equal(others.length, 0);
  assert.equal(one.added, 2);
  assert.deepEqual(
    one.hunks.map(({ start, count, lines }) => [start, count, lines.length]),
    [[30, 40, 3]],
  );
});

test("aFileIsNotGone whenItsPatchHasNoTargetLine", () => {
  const [one] = files("diff --git a/img.png b/img.png\nBinary files differ");

  assert.deepEqual(one, {
    path: "img.png",
    added: 0,
    removed: 0,
    hunks: [],
    gone: false,
  });
});

test("theTargetLineNamesThePath whenTheDiffLineHoldsASpaceBeforeASlash", () => {
  const [one] = files(
    "diff --git a/dir b/x.txt b/dir b/x.txt\n--- a/dir b/x.txt\n+++ b/dir b/x.txt",
  );

  assert.equal(one.path, "dir b/x.txt");
  assert.equal(one.gone, false);
});

test("textBeforeTheFirstFileIsIgnored whenAPreambleLooksLikeATarget", () => {
  assert.deepEqual(files("+++ b/preamble.txt\n"), []);
});
