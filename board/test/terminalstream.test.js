import assert from "node:assert/strict";
import { test } from "node:test";
import { resized, streamUrl, typed } from "../terminal/stream.js";

test("streamUrl asks for a task's window over ws on http, with the screen's size", () => {
  assert.equal(
    streamUrl(
      { protocol: "http:", host: "localhost:8290" },
      { task: "ABC-42" },
      { cols: 120, rows: 30 },
    ),
    "ws://localhost:8290/api/windows/stream?task=ABC-42&cols=120&rows=30",
  );
});

test("streamUrl asks for an opened window by name over wss on https", () => {
  assert.equal(
    streamUrl(
      { protocol: "https:", host: "jagt.example" },
      { task: "ABC-42", name: "login" },
      { cols: 80, rows: 24 },
    ),
    "wss://jagt.example/api/windows/stream?name=login&cols=80&rows=24",
  );
});

test("typed sends what was typed as one data frame, escapes kept", () => {
  assert.deepEqual(JSON.parse(typed("ls\r\u001b[A")), {
    type: "data",
    data: "ls\r\u001b[A",
  });
});

test("resized sends the screen's columns and rows", () => {
  assert.deepEqual(JSON.parse(resized({ cols: 100, rows: 40 })), {
    type: "resize",
    cols: 100,
    rows: 40,
  });
});

test("streamUrl asks for an empty task when the target names neither", () => {
  assert.equal(
    streamUrl(
      { protocol: "http:", host: "localhost:8290" },
      {},
      { cols: 80, rows: 24 },
    ),
    "ws://localhost:8290/api/windows/stream?task=&cols=80&rows=24",
  );
});
