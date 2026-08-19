/**
 * Platform strategies — the ONLY place OS-/app-specific code is allowed.
 *
 * <p>Porting jagt to another OS or terminal means implementing three
 * interfaces and wiring them via configuration; the core (git, state, tmux,
 * MCP) never changes:
 *
 * <ul>
 *   <li>{@link dev.jagt.orchestrator.port.UserNotifier} — OS push
 *       notifications. Selected by {@code orchestrator.platform}
 *       (default {@code macos} → {@code macos.MacNotifier} via osascript;
 *       a Linux impl would use notify-send and
 *       {@code @ConditionalOnProperty(name = "orchestrator.platform", havingValue = "linux")}).</li>
 *   <li>{@link dev.jagt.orchestrator.port.TerminalDriver} — how the
 *       agents' tmux sessions become visible. Selected by
 *       {@code orchestrator.terminal} (default {@code warp} →
 *       {@code macos.WarpTerminalDriver}).</li>
 *   <li>{@link dev.jagt.orchestrator.port.EditorDriver} — how a worktree
 *       opens for human review. {@code CliEditorDriver} is the generic default
 *       (any CLI launcher via {@code orchestrator.editor-command}); replace it
 *       only if a launcher-command is not enough.</li>
 * </ul>
 *
 * <p>Contract ground rules for every implementation:
 * <ul>
 *   <li>NEVER inject keyboard input or synthesize keystrokes — it races with
 *       the human typing. Addressed window operations (raise/close a specific
 *       window) are fine.</li>
 *   <li>Visibility calls are best-effort UX: log-and-continue on failure,
 *       never fail the orchestration flow that triggered them.</li>
 *   <li>Implementations must be safe to call repeatedly (the orchestrator
 *       retries and self-heals); debounce internally where the UI needs it.</li>
 * </ul>
 */
package dev.jagt.orchestrator.adapter;
