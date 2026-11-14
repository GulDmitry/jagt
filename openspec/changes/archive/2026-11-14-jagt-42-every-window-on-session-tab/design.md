## Context

Windows jagt opens today: `LoginCommand` (`openCommandWindow` with the lost reach's `fix`), `ScenarioWindows`
(`openShellWindow`, name `acceptance-<project>`), and an MCP login, which `ClaudeMcpHealthProbe` only names in a
log `fix` (`/mcp` in the human's own session). The Session tab and `say` take a task id alone
(`TypedLineController`, `AgentSessions.screen`/`say`).

Tool finding, 2026-11-14: `glab auth login --device` exists (glab 1.130.0, GitLab 17.9 or later; shows a one-time code
and URL); `gh auth login --web` is gh's device flow, `--clipboard` copies the code, `GH_BROWSER`/`BROWSER` name the
browser it opens.

## Decisions

- The windows jagt opens that are no task's have fixed names: `login` (any lost reach's login, an MCP login too) and
  `acceptance-<project>`. `OpenedWindows` lists the open ones and reads and types by name; `/api/windows` serves it.
  A task's session stays on `/api/tasks/*`.
- A GitHub login runs as `env GH_BROWSER=true gh auth login --web`: the one-time code and URL print in the window and
  the Session tab reads them whole.
- A GitLab login runs `sh -c 'glab auth login --device || glab auth login'`: the CLI refusing the device flag
  falls back to its own prompt, which `say` answers. No version is guessed.
- The board reads the window's screen; no login output is parsed into a field of its own.

## Classes that change

| class | change |
|-------|--------|
| `usecase/service/OpenedWindows` | new: names, screen and say for the login and scenarios windows |
| `usecase/command/LoginCommand` | takes its window name from `OpenedWindows.LOGIN` |
| `surface/board/WindowsController` | new: `GET /api/windows`, `/api/windows/screen`, `POST /api/windows/say` |
| `adapter/codehost/HostCli` | the login function gives the whole fix command |
| `adapter/codehost/GitHubCli` | login command sets `GH_BROWSER=true` |
| `adapter/codehost/GitLabCli` | login command tries `--device`, then the plain prompt |
| `static/` (Pane, tabs, render, api, i18n) | the Session tab's window buttons and say to a window |

## Open

- No code opens an MCP login window yet (`ClaudeMcpHealthProbe` only logs its fix); when it does, it uses `login`.
