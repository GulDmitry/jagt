package dev.jagt.orchestrator.shell;

import dev.jagt.orchestrator.assistant.MasterAssistant;
import dev.jagt.orchestrator.assistant.MasterAssistant.TicketFacts;
import dev.jagt.orchestrator.mcp.OrchestratorTools;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.DashboardRenderer;
import com.googlecode.lanterna.SGR;
import com.googlecode.lanterna.TerminalPosition;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.DefaultTerminalFactory;
import com.googlecode.lanterna.terminal.Terminal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The Master control terminal: a deterministic JLine REPL running in the backend process. It parses a
 * fixed grammar and calls {@link OrchestratorTools} directly (same JVM — no LLM, no MCP round-trip, no
 * drift). Every command prints its result then the dashboard, so the terminal always ends on current
 * state. Callers here are the Master (never a sub-agent), so the {@code callerTaskId} scoping arg is
 * always {@code null}.
 *
 * <p>{@code ship}/{@code review} are not here yet: they delegate GitLab/Jira work to the sub-agent
 * (its own MCP) via {@code write_task_context}; that lands with the delegation layer.
 */
@Component
@Order(Integer.MAX_VALUE)
public class MasterShell implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MasterShell.class);

    private final OrchestratorTools tools;
    private final DashboardRenderer dashboard;
    private final ConfigService configService;
    private final MasterAssistant assistant;
    private final ConfigurableApplicationContext context;

    public MasterShell(OrchestratorTools tools, DashboardRenderer dashboard, ConfigService configService,
                       MasterAssistant assistant, ConfigurableApplicationContext context) {
        this.tools = tools;
        this.dashboard = dashboard;
        this.configService = configService;
        this.assistant = assistant;
        this.context = context;
    }

    /**
     * Rows reserved for the command-output + input area BELOW the dashboard, so the dashboard region never
     * eats the whole screen when there are many tasks. The full-screen TUI stacks the output log on top,
     * the dashboard table beneath it, and the input line at the very bottom — all in one Lanterna
     * back-buffer that is re-laid-out and fully redrawn on resize (no scroll-region/anchor fragility).
     * Configurable via {@code dashboardReservedRows} in config.json; read at startup (default 17).
     */
    private int commandRows = 17;

    /** Cap the in-memory output log so a long-running session can't grow it without bound. */
    private static final int MAX_LOG_LINES = 2000;

    @Override
    public void run(ApplicationArguments args) {
        ConfigService.ConfigFile config = configService.load();
        int refreshSeconds = config.dashboardRefreshSecondsOrDefault();
        this.commandRows = config.dashboardReservedRowsOrDefault();

        Screen screen = null;
        try {
            // A TUI needs a real interactive terminal. Under `gradlew bootRun` (Gradle pipes stdout) or any
            // other non-TTY there is no console — fall back to a plain line REPL instead of a garbled TUI.
            if (System.console() == null) {
                runInlineFallback();
            } else {
                // Force a text terminal: never let Lanterna fall back to a Swing window (it would if a GUI
                // is present) — the Master is a terminal app.
                Terminal terminal = new DefaultTerminalFactory().setForceTextTerminal(true).createTerminal();
                screen = new TerminalScreen(terminal);
                screen.startScreen();
                // 0 (or less) = no periodic refresh: redraw only on input/resize (matches the config doc).
                long refreshMillis = refreshSeconds > 0 ? refreshSeconds * 1000L : Long.MAX_VALUE;
                runTui(screen, refreshMillis);
            }
        } catch (IOException e) {
            log.warn("Master shell terminal unavailable — running inline ({})", e.getMessage());
            runInlineFallback();
        } finally {
            if (screen != null) {
                try {
                    screen.stopScreen();
                } catch (IOException e) {
                    log.debug("screen stop failed: {}", e.toString());
                }
            }
            stopBackend();
        }
        // context.close() (in stopBackend) stops the web server; the hard-exit then guarantees the process
        // ends even if a non-daemon thread lingers. Safe here — the context is already closed, so the JVM
        // shutdown hook has nothing to close and stays quiet.
        System.exit(0);
    }

    /**
     * The full-screen TUI loop: one Lanterna back-buffer holding the command-output log (top), the
     * dashboard table (middle), and the input line (bottom). Each iteration polls a keystroke, applies it,
     * repaints when the refresh interval elapses, and — crucially — calls {@code doResizeIfNecessary} + a
     * full redraw, so a terminal resize just re-lays-out cleanly (the whole class of JLine scroll-region /
     * ghost / prompt-anchor bugs cannot occur when every frame is drawn from scratch into a back buffer).
     */
    private void runTui(Screen screen, long refreshMillis) throws IOException {
        List<String> outputLog = new ArrayList<>();
        outputLog.add("jagt — type a command ('help'); 'exit' stops the backend (agents keep running).");
        StringBuilder input = new StringBuilder();
        long lastRefresh = System.currentTimeMillis();
        render(screen, outputLog, input.toString(), true);
        while (true) {
            TerminalSize resized = screen.doResizeIfNecessary();   // null unless the terminal size changed
            KeyStroke key = screen.pollInput();
            if (key != null) {
                KeyType type = key.getKeyType();
                if (type == KeyType.EOF) {
                    return;
                }
                if (type == KeyType.Character) {
                    input.append(key.getCharacter());
                } else if (type == KeyType.Backspace && input.length() > 0) {
                    input.deleteCharAt(input.length() - 1);
                } else if (type == KeyType.Enter) {
                    String line = input.toString().strip();
                    input.setLength(0);
                    if (line.equals("exit") || line.equals("quit")) {
                        return;
                    }
                    if (!line.isEmpty()) {
                        outputLog.add("jagt> " + line);
                        String out = dispatch(line);
                        if (!out.isBlank()) {
                            outputLog.addAll(List.of(out.split("\\R")));
                        }
                        if (outputLog.size() > MAX_LOG_LINES) {   // bound the log so a long session can't leak
                            outputLog.subList(0, outputLog.size() - MAX_LOG_LINES).clear();
                        }
                    }
                }
                render(screen, outputLog, input.toString(), resized != null);
                continue;
            }
            long now = System.currentTimeMillis();
            if (resized != null || now - lastRefresh >= refreshMillis) {
                lastRefresh = now;
                render(screen, outputLog, input.toString(), resized != null);
            }
            try {
                Thread.sleep(30);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    /**
     * Draw the whole screen into Lanterna's back buffer: output log on top, dashboard table beneath it
     * (capped so {@code commandRows} rows stay for output+input; overflow → a "… +N" line), and the input
     * line at the very bottom. NEVER {@code screen.clear()} — that forces a full terminal wipe on the next
     * refresh (visible flicker on every keystroke/tick). Instead every row is drawn (empties blanked) and
     * {@code refresh(DELTA)} sends only the cells that actually changed; {@code full} (first paint / resize)
     * uses COMPLETE.
     */
    private void render(Screen screen, List<String> outputLog, String input, boolean full) throws IOException {
        TerminalSize size = screen.getTerminalSize();
        int height = size.getRows();
        int width = size.getColumns();
        TextGraphics g = screen.newTextGraphics();

        String[] dash = dashboard.render().split("\\R");
        int body = Math.max(1, height - 1);                       // rows above the input line
        int dashRows = Math.min(dash.length, Math.max(1, body - commandRows));
        int outputRows = body - dashRows;                          // output log gets whatever is left on top
        int dashTop = outputRows;

        int from = Math.max(0, outputLog.size() - outputRows);     // last outputRows lines, oldest first
        for (int i = 0; i < outputRows; i++) {                     // draw EVERY row (blank the empties): with
            String line = from + i < outputLog.size() ? outputLog.get(from + i) : "";   // no clear(), unwritten
            // A colored command echo ("jagt> …") heads each block — that IS the visual separator; errors red.
            TextColor c = line.startsWith("jagt> ") ? TextColor.ANSI.CYAN_BRIGHT
                    : line.startsWith("error:") ? TextColor.ANSI.RED_BRIGHT
                    : TextColor.ANSI.DEFAULT;
            put(g, i, line, width, c, line.startsWith("jagt> "));
        }
        for (int i = 0; i < dashRows; i++) {
            String text = i < dash.length ? dash[i] : "";
            if (dash.length > dashRows && i == dashRows - 1) {     // more tasks than fit → collapse the tail
                text = "  … +" + (dash.length - dashRows + 1) + " more — see all: curl localhost:8290/status";
            }
            put(g, dashTop + i, text, width, dashColor(text), text.startsWith("jagt orchestrator"));
        }
        put(g, height - 1, "jagt> ", width, TextColor.ANSI.CYAN_BRIGHT, true);     // input line: prompt…
        g.putString(6, height - 1, fit(input, Math.max(0, width - 6)));            // …then the typed text
        screen.setCursorPosition(new TerminalPosition(Math.min(6 + input.length(), Math.max(0, width - 1)), height - 1));
        // DELTA writes only changed cells (smooth, no flicker); COMPLETE redraws all on first paint / resize.
        screen.refresh(full ? Screen.RefreshType.COMPLETE : Screen.RefreshType.DELTA);
    }

    /** Color for one dashboard line: header green, action-needed rows yellow, the "…+N" overflow yellow. */
    private static TextColor dashColor(String text) {
        if (text.startsWith("jagt orchestrator")) {
            return TextColor.ANSI.GREEN_BRIGHT;
        }
        if (text.contains("your move") || text.contains("… +")) {
            return TextColor.ANSI.YELLOW_BRIGHT;
        }
        return TextColor.ANSI.DEFAULT;
    }

    /** Draw one full-width row in a color (bold optional), then reset the graphics to defaults. */
    private static void put(TextGraphics g, int row, String text, int width, TextColor color, boolean bold) {
        g.setForegroundColor(color);
        if (bold) {
            g.enableModifiers(SGR.BOLD);
        }
        g.putString(0, row, fit(text, width));
        g.disableModifiers(SGR.BOLD);
        g.setForegroundColor(TextColor.ANSI.DEFAULT);
    }

    /** Pad/truncate to exactly the terminal width so a full-screen redraw leaves no stale characters. */
    private static String fit(String s, int width) {
        if (width <= 0) {
            return "";
        }
        return s.length() > width ? s.substring(0, width) : s + " ".repeat(width - s.length());
    }

    /**
     * No-TTY fallback (e.g. {@code gradlew bootRun}, where Gradle pipes stdout): a plain line REPL that
     * prints each command's result followed by the dashboard. There is no interactive terminal to draw on.
     */
    private void runInlineFallback() {
        log.info("No interactive terminal — Master shell running inline (dashboard after each command).");
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        System.out.println(withDashboard("", dashboard.render()));
        try {
            String line;
            while ((line = in.readLine()) != null) {
                String cmd = line.strip();
                if (cmd.isEmpty()) {
                    continue;
                }
                if (cmd.equals("exit") || cmd.equals("quit")) {
                    break;
                }
                System.out.println(withDashboard(dispatch(cmd), dashboard.render()));
            }
        } catch (IOException e) {
            log.warn("inline shell input closed: {}", e.getMessage());
        }
    }

    /**
     * Stop the backend when the shell exits. Close the Spring context HERE, while the fat-jar classloader
     * is still live: if the JVM shutdown hook closed it instead, its close-time logging would load a
     * logback class (ThrowableProxy) for the first time from the nested jar while that loader is already
     * closing → NoClassDefFoundError. Closing now does that logging early and quietly. Agents live in tmux.
     */
    void stopBackend() {
        log.info("Master shell exited — stopping backend (agents keep running in tmux).");
        context.close();
    }

    private String dispatch(String line) {
        List<String> tok = List.of(line.split("\\s+"));
        String cmd = tok.get(0);
        try {
            String result = switch (cmd) {
                case "status" -> "";
                case "help" -> help();
                case "do" -> doTask(tok);
                case "resume" -> resumeTask(tok);
                case "review" -> reviewTask(tok);
                case "ship" -> tools.ship(arg(tok, 1, "ship <ticket>"));
                case "focus" -> tools.focusTask(arg(tok, 1, "focus <ticket>"));
                case "ide" -> tools.openInIde(arg(tok, 1, "ide <ticket> [diff]"),
                        tok.contains("diff") ? "diff" : "project", null);
                case "deploy" -> tools.deployTask(arg(tok, 1, "deploy <ticket>"), null);
                case "respawn" -> tools.openTaskTab(arg(tok, 1, "respawn <ticket>"), null);
                case "done" -> tools.removeTask(arg(tok, 1, "done <ticket>"), null);
                default -> "unknown command '" + cmd + "' — try 'help'";
            };
            return result;
        } catch (IllegalArgumentException | IllegalStateException e) {
            return "error: " + e.getMessage();
        }
    }

    /** Inline-fallback formatting (no pinned region): the command result then the dashboard (blank result = dashboard alone). */
    static String withDashboard(String result, String dashboardText) {
        return result.isBlank() ? dashboardText : result + "\n\n" + dashboardText;
    }

    /** A bare issue key like {@code ABC-123} — used only to skip the read on the fast path, never parsed out of a URL. */
    private static final Pattern KEY_REF = Pattern.compile("[A-Za-z][A-Za-z0-9]*-[0-9]+");

    String doTask(List<String> tok) {
        String ref = arg(tok, 1, "do <ticket|url> [project] [plan]");
        String mode = tok.contains("plan") ? "plan" : null;
        String explicit = tok.stream().skip(2).filter(t -> !t.equals("plan")).findFirst().orElse(null);
        boolean bareKey = KEY_REF.matcher(ref).matches();

        // Fast path: a bare key + explicit project needs no read — the key IS the task id.
        if (bareKey && explicit != null) {
            return tools.initializeTask(ref, resolveProject(explicit),
                    "Read " + ref + " via your issue-tracker MCP and implement it.", mode, null, null);
        }
        // Otherwise read the item. `ref` may be a KEY or a URL to any tracker — the assistant follows it
        // and returns the canonical key (jagt names the branch/worktree by it; it is NOT parsed from a URL).
        var facts = assistant.readTicket(ref);
        if (facts.isPresent() && !facts.get().exists()) {
            return "error: could not read " + ref + " (bad/inaccessible URL or unknown key?)";
        }
        if (facts.isPresent()) {
            TicketFacts f = facts.get();
            // Name the task by the canonical key the assistant read back; if it returned none but the
            // caller already gave a bare key, that key is a fine task id (a URL has no such fallback).
            String taskId = f.key() != null && !f.key().isBlank() ? f.key() : (bareKey ? ref : null);
            if (taskId == null) {
                return "error: read " + ref + " but the assistant returned no issue key to name the task";
            }
            String project = explicit != null ? resolveProject(explicit) : resolveByLabels(f);
            String instructions = "Implement " + taskId + " — \"" + f.title()
                    + "\". Read it via your issue-tracker MCP for full details, then work.";
            return tools.initializeTask(taskId, project, instructions, mode, null, f.title());
        }
        // Assistant unavailable: only a bare key can proceed — a URL has no derivable task id without it.
        if (!bareKey) {
            return "error: assistant unavailable — pass an issue key (not a URL), or add the project";
        }
        return tools.initializeTask(ref, resolveProject(explicit),
                "Read " + ref + " via your issue-tracker MCP and implement it.", mode, null, null);
    }

    /**
     * Reopened MR: `resume <mr-url>` — the MR is enough. The assistant reads it for the source branch
     * (= the task) and project; jagt resumes that branch + links the MR at CI_POLLING (no new MR). An
     * explicit ticket token may be given to skip the lookup.
     */
    String resumeTask(List<String> tok) {
        String mrUrl = tok.stream().skip(1).filter(t -> t.startsWith("http")).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("usage: resume <mr-url>"));
        String ticket = tok.stream().skip(1).filter(t -> !t.startsWith("http")).findFirst().orElse(null);
        // Read the MR (one MCP call jagt already needs for the branch) — it also carries the title, so a
        // resumed task shows one on the dashboard just like a `do` task, not a blank.
        String title = null;
        var mr = assistant.readMergeRequest(mrUrl);
        if (mr.isPresent() && mr.get().exists()) {
            title = mr.get().title();
            if (ticket == null) {
                ticket = mr.get().sourceBranch();
            }
        } else if (ticket == null) {
            return "error: could not read MR (or not found): " + mrUrl;
        }
        return tools.resumeTask(ticket, mrUrl, title);
    }

    /**
     * MR sweep: pull the MR's pipeline + unresolved comments (headless) and relay ONE brief to the
     * agent via task_context.md, which fixes locally and drafts replies. Nothing is pushed/posted.
     */
    private String reviewTask(List<String> tok) {
        String ticket = arg(tok, 1, "review <ticket>");
        String mrUrl = tools.taskMrUrl(ticket);
        if (mrUrl == null || mrUrl.isBlank()) {
            return "error: no MR linked to " + ticket + " — `ship` or `resume <mr-url>` first";
        }
        var sweep = assistant.readReview(mrUrl);
        if (sweep.isEmpty() || !sweep.get().exists()) {
            return "error: could not read the MR review for " + mrUrl;
        }
        var r = sweep.get();
        boolean pipelineFailed = r.pipelineStatus() != null && r.pipelineStatus().toLowerCase().contains("fail");
        if (r.comments().isEmpty() && !pipelineFailed) {
            return "review " + ticket + ": pipeline " + r.pipelineStatus()
                    + ", no unresolved comments — your move: `deploy` or `done`";
        }
        StringBuilder brief = new StringBuilder("Review round for MR ").append(mrUrl).append(".\n");
        if (pipelineFailed) {
            brief.append("Pipeline: ").append(r.pipelineStatus()).append(" — fix the failing build.\n");
        }
        if (!r.comments().isEmpty()) {
            brief.append("Unresolved comments — fix the valid ones LOCALLY (no commit/push). For EACH"
                    + " comment write a block in review_replies.md: the original comment (with its thread"
                    + " link if available) followed by the reply you intend to post:\n");
            r.comments().forEach(c -> brief.append("- ").append(c).append('\n'));
        }
        brief.append("When done, set status REVIEW_PENDING. Do NOT push or post anything yourself.");
        tools.writeTaskContext(ticket, brief.toString());
        return "review " + ticket + ": relayed " + r.comments().size() + " comment(s), pipeline "
                + r.pipelineStatus() + " -> agent";
    }

    /** Picks the jagt project whose configured labels intersect the ticket's labels (or Jira key). */
    private String resolveByLabels(TicketFacts f) {
        Map<String, List<String>> projectLabels = new java.util.LinkedHashMap<>();
        configService.load().projects().forEach((k, v) -> projectLabels.put(k, v.labels()));
        List<String> matches = projectsMatching(f, projectLabels);
        if (matches.size() == 1) {
            return matches.get(0);
        }
        if (matches.isEmpty()) {
            throw new IllegalArgumentException("no project matches ticket labels " + f.labels()
                    + " — specify: do <ticket> <project>");
        }
        throw new IllegalArgumentException("ticket labels match multiple projects " + matches
                + " — specify: do <ticket> <project>");
    }

    /** Pure: the project keys whose labels intersect the ticket's labels or its Jira project key. */
    static List<String> projectsMatching(TicketFacts f, Map<String, List<String>> projectLabels) {
        Set<String> tokens = new HashSet<>(f.labels());
        tokens.add(f.jiraProject());
        return projectLabels.entrySet().stream()
                .filter(e -> e.getValue() != null && e.getValue().stream().anyMatch(tokens::contains))
                .map(Map.Entry::getKey)
                .toList();
    }

    private String resolveProject(String project) {
        Set<String> keys = configService.load().projects().keySet();
        if (project != null && !project.isBlank()) {
            if (!keys.contains(project)) {
                throw new IllegalArgumentException("unknown project '" + project + "'. Configured: " + keys);
            }
            return project;
        }
        if (keys.size() == 1) {
            return keys.iterator().next();
        }
        throw new IllegalArgumentException("multiple projects " + keys + " — specify one: do <ticket> <project>");
    }

    private static String arg(List<String> tok, int i, String usage) {
        if (tok.size() <= i || tok.get(i).isBlank()) {
            throw new IllegalArgumentException("usage: " + usage);
        }
        return tok.get(i);
    }

    private static String help() {
        List<String> lines = new ArrayList<>();
        lines.add("commands (task = ticket id or alias):");
        lines.add("  status                       show the dashboard");
        lines.add("  do <ticket> [project] [plan] spin up a sub-agent in a worktree");
        lines.add("  resume <mr-url>              reopened MR: resume its branch + link it -> CI_POLLING");
        lines.add("  focus <ticket>               jump to the agent's window (talk to it there)");
        lines.add("  ship <ticket>                approve: agent commits (pattern title), pushes, MR, posts replies");
        lines.add("  review <ticket>              pull the MR's pipeline + comments, relay them to the agent");
        lines.add("  ide <ticket> [diff]          open worktree project (live Git diff); `diff` = static snapshot vs base");
        lines.add("  deploy <ticket>              merge task branch into deployBranch + push");
        lines.add("  respawn <ticket>             restart a dead agent session");
        lines.add("  done <ticket>                full cleanup (window, worktree, state; branch kept)");
        lines.add("  help | quit                  this reference | detach (agents keep running)");
        return String.join("\n", lines);
    }
}
