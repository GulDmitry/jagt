# Add a language

[← README](../README.md)

The board's own words come from one catalog file per language. English is the default and the fallback.

1. Copy `en.json` to `<code>.json`, the language's BCP 47 code: `de.json`, `pt-BR.json`.
   It is in the repository under `orchestrator-backend/surface/src/main/resources/static/i18n/`.
2. Translate the values; keep the keys and the `{name}` placeholders. Set `language.name` to the language's own name.
3. Drop the file in `<root>/i18n/`, jagt's own root, the folder holding `jagt.yml`.
   A file there overrides a shipped catalog of the same code or adds a new one; no rebuild.
4. Reload the board and choose the language on the gear. The choice is kept in that browser.

A key the file lacks shows English. Logs, reports, verb names and times are not translated.
