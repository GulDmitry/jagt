# 0120 — jagt.yml is edited as text, one block per project

[← decisions](README.md) · rule: [`project-setup`](../../openspec/specs/project-setup/spec.md#requirement-a-project-is-added-from-the-board)

## Status

Proposed 2026-10-31; accepted by the commit landing edit and remove. Records the writer built 2026-10-16 (`ProjectSetup`).

## Context

- `jagt.yml` is the human's: comments, blank lines, key order, quoting and `# ──` headers are theirs.
- The board adds, edits and removes a project entry; a write must move no other byte.
- SnakeYAML 2.4 and 2.6 sit in the Gradle cache (checked 2026-10-31): `load()` drops comments even with `processComments`;
  only `compose()` → `serialize(Node)` keeps them, and it re-emits style, flow lists and blank lines: not byte-stable.
- eo-yaml `com.amihaiemil.web:eo-yaml` 8.0.6, latest on Maven Central (checked 2026-10-31, published 2024-08-15): keeps comments,
  but reprints the whole document, and is a second YAML library for one write.

## Decision

- A project's block is the lines from `<indent>key:` to the next line at that indent or shallower; add inserts one, edit replaces
  those lines, remove deletes them. Every other line stays as it was.
- The file is read back after each write; a result that does not read is put back and refused with the parser's words.
- No YAML library writes the file; the one that reads it stays Spring's.

## Rejected

- SnakeYAML node round-trip: re-emits style and blank lines, so a write moves bytes it was not asked to.
- eo-yaml: reprints the document; a dependency for one write.
- A jagt-owned `projects.yml`: a second home for the one list `jagt.yml` holds; Getting started stays one file.

## Reopen when

- A file the text writer cannot place appears twice, or a library proves byte-stable on `jagt.yml.dist` plus a commented copy.
