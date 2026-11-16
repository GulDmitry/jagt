## ADDED Requirements

### Requirement: The board answers with a Content-Security-Policy of its own origin
Every response of the board SHALL carry a `Content-Security-Policy` that loads script, image, font, frame and connection from the
board's own origin only. A board page SHALL run no inline script but its import map, which the policy admits by hash; the vendored
editor and diagram libraries inject style, so `style-src` also admits inline style, and the favicon dot is a `data:` image.
#### Scenario: A page asks for a foreign script
- **WHEN** the board answers any request
- **THEN** its `Content-Security-Policy` names `'self'` for `default-src` and `script-src`, no `unsafe-eval` and no host
