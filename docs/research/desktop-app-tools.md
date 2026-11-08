# How comparable tools ship — checked 2026-11-08

[← desktop-app.md](desktop-app.md) §6.9; `[sN]` resolves in [desktop-app-sources.md](desktop-app-sources.md).

| tool | install: macOS · Linux · Windows | one mechanism, three OS | updates | runtime | source |
|---|---|---|---|---|---|
| Claude Code | `curl …/install.sh \| bash` · same · `irm …/install.ps1 \| iex`; also cask, winget, apt/dnf/apk, npm | yes: the native installer | native: in the background, channel `latest` or `stable`; managers by hand or on request | native binary, no Node | [s28] |
| Codex CLI | `curl …/codex/install.sh \| sh` · same · `irm …/codex/install.ps1 \| iex`; also npm, cask | yes | re-run the install line | native binary, a tarball per platform on GitHub releases | [s38] |
| Ollama | dmg app · `curl …/install.sh \| sh` · `OllamaSetup.exe` | no: three | mac, Windows download themselves, "Restart to update"; Linux re-runs the script | own binary | [s29][s39] |
| Docker Desktop | dmg · deb/rpm · exe installer, Store | no | checks by default, downloads only if asked | own | [s31][s61] |
| Tailscale | App Store or standalone package · `curl …/install.sh \| sh` · exe, msi | no | `tailscale update`; an auto-update toggle on Windows and macOS, `tailscale set --auto-update` on Linux | own | [s40] |
| Syncthing | zip, macOS app · tarball, apt repository · zip, Windows Setup, SyncTrayzor | no: one binary, many wrappers | upgrades itself within 24 h, `syncthing --upgrade`, channels stable/candidate; apt builds: none | Go binary | [s30][s41] |
| JupyterLab Desktop | dmg · deb/rpm/snap · exe, winget | no | from GitHub releases; no in-app update documented | Electron | [s32] |
| Bun | `curl …/install \| bash` · same · `irm bun.sh/install.ps1 \| iex`; also npm, brew, scoop | yes | `bun upgrade`, `--canary` | native | [s42] |
| Deno | `curl …/install.sh \| sh` · same · `irm …/install.ps1 \| iex`; also brew, winget, scoop, npm | yes | `deno upgrade` replaces the executable from GitHub releases | native | [s43] |
| rustup | `curl …/sh.rustup.rs \| sh` · same · `rustup-init.exe` | nearly: a script and an exe | `rustup update`, `rustup self update` | native | [s44] |
| uv | `curl …/install.sh \| sh` · same · `irm …/install.ps1 \| iex`; also pipx, brew, winget, scoop | yes | `uv self update`, standalone installs only | native | [s45] |
| JetBrains Toolbox | dmg · tar.gz · exe | no | the IDEs in the background; Toolbox itself: unknown | unknown | [s46] |
| VS Code | dmg · deb/rpm installing its repository, snap · User Setup exe | no | built in, weekly, on macOS and Windows; Linux by the repository | Electron | [s47] |
| Zed | dmg, cask · `curl …/install.sh \| sh` · exe, winget | no | checks itself, `auto_update` default true | native | [s48] |
| Warp | dmg, cask · deb/rpm/pacman repository, AppImage · exe, winget | no | "All installation options include auto-update" | native | [s49] |
| LM Studio | per-OS installers, Linux an AppImage; headless `llmster`, `lms` daemon | no | unknown | unknown | [s37][s50] |
| JReleaser-published tools | tap, scoop, winget, sdkman, jbang, snap, flatpak, AppImage … per release | no: one release, many managers | each by its manager | jlink or jpackage assembler | [s51] |
| Conveyor apps | zip + Sparkle · deb + apt, tarball · MSIX, exe | one config, three packages | built in: Sparkle, MSIX in the background, apt; tarball none | jlink JDK | [s52] |
| jbang | `curl …/sh.jbang.dev \| bash -s - app setup` · same · `iex "& { $(iwr https://ps.jbang.dev) } app setup"`; also sdkman, brew, choco, scoop | yes, then `jbang app install <gav>` | self-update unknown | downloads a JDK | [s53] |
| Coursier | `cs` native launcher per arch, brew · same · installer or zip; `cs setup` installs a JVM 21 | no | `cs update` by hand | native launcher | [s54] |

The pattern: a developer tool ships one script per shell over its own release feed and updates itself; a GUI app ships an installer per OS
and updates itself; a manager install updates by the manager [s28].
