package dev.jagt.orchestrator.shell;

import dev.jagt.orchestrator.assistant.MasterAssistant;
import dev.jagt.orchestrator.assistant.MasterAssistant.TicketFacts;
import dev.jagt.orchestrator.mcp.OrchestratorTools;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.DashboardRenderer;
import dev.jagt.orchestrator.service.ReviewSweepService;
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
import com.googlecode.lanterna.terminal.ansi.UnixLikeTerminal;
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
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
    private final ReviewSweepService reviewSweep;
    private final ConfigurableApplicationContext context;

    public MasterShell(OrchestratorTools tools, DashboardRenderer dashboard, ConfigService configService,
                       MasterAssistant assistant, ReviewSweepService reviewSweep,
                       ConfigurableApplicationContext context) {
        this.tools = tools;
        this.dashboard = dashboard;
        this.configService = configService;
        this.assistant = assistant;
        this.reviewSweep = reviewSweep;
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

    /** Every command, for Tab-completion of the first word. */
    private static final List<String> COMMANDS = List.of("status", "do", "resume", "review", "ship",
            "focus", "ide", "deploy", "respawn", "done", "help", "quit", "exit");
    /** Commands whose first argument is an EXISTING task (so Tab completes its alias/id); `do`/`resume`
     *  take a new ticket/URL, not a current task. */
    private static final Set<String> TASK_ARG_COMMANDS = Set.of(
            "review", "ship", "focus", "ide", "deploy", "respawn", "done");

    @Override
    public void run(ApplicationArguments args) {
        ConfigService.ConfigFile config = configService.load();
        int refreshSeconds = config.dashboard().refreshSecondsOrDefault();
        this.commandRows = config.dashboard().reservedRowsOrDefault();

        Screen screen = null;
        try {
            // A TUI needs a real interactive terminal. Under `gradlew bootRun` (Gradle pipes stdout) or any
            // other non-TTY there is no console — fall back to a plain line REPL instead of a garbled TUI.
            if (System.console() == null) {
                runInlineFallback();
            } else {
                Terminal terminal = new DefaultTerminalFactory()
                        // Force a text terminal: never let Lanterna fall back to a Swing window (it would if
                        // a GUI is present) — the Master is a terminal app.
                        .setForceTextTerminal(true)
                        // TRAP delivers Ctrl-C to us as a keystroke (abort the input line) instead of killing
                        // the JVM (Lanterna's default is CTRL_C_KILLS_APPLICATION).
                        .setUnixTerminalCtrlCBehaviour(UnixLikeTerminal.CtrlCBehaviour.TRAP)
                        .createTerminal();
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
                    screen.stopScreen();                          // restore the terminal (leave the alt screen)
                } catch (Throwable t) {                           // best-effort — a corrupted jar may fail here
                    log.debug("screen stop failed: {}", t.toString());
                }
            }
            shutdownBackend();                                    // closes the context and GUARANTEES the JVM exits
        }
    }

    /**
     * Stop the backend and GUARANTEE the process dies. {@code context.close()} runs on a side thread with a
     * hard cap: if {@code ./gradlew build} rewrote the fat jar IN PLACE while this JVM was running (see
     * CLAUDE.md), lazy class loading is corrupted and close() can throw {@code NoClassDefFoundError} or hang
     * — which used to leave non-daemon Tomcat threads alive, so {@code exit} hung until killed by hand.
     * Either way we {@code halt(0)}: it skips shutdown hooks and needs no class loading. Agents live in
     * their own tmux processes, so halting this JVM never touches them.
     */
    private void shutdownBackend() {
        Thread closer = new Thread(() -> {
            try {
                stopBackend();
            } catch (Throwable t) {
                log.debug("backend stop failed (jar may have been rebuilt in place): {}", t.toString());
            }
        }, "jagt-shutdown");
        closer.setDaemon(true);
        closer.start();
        try {
            closer.join(3000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        Runtime.getRuntime().halt(0);
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
        LineEditor editor = new LineEditor();
        List<String> history = new ArrayList<>();
        int histIdx = 0;                                           // navigation cursor; == size() means "new line"
        // Commands (resume/review/do spawn a headless `claude` and take tens of seconds) run on a worker so
        // the UI thread keeps polling input + repainting — otherwise Enter freezes the whole TUI until done.
        ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "jagt-command");
            t.setDaemon(true);
            return t;
        });
        Future<String> pending = null;
        String runningLabel = null;
        long runningSince = 0;
        long lastRender = System.currentTimeMillis();
        render(screen, outputLog, editor, null, true);
        try {
            while (true) {
                TerminalSize resized = screen.doResizeIfNecessary();   // null unless the size changed

                boolean drained = false;
                if (pending != null && pending.isDone()) {             // command finished → drain it onto the UI
                    try {
                        String out = pending.get();
                        if (out != null && !out.isBlank()) {
                            outputLog.addAll(List.of(out.split("\\R")));
                        }
                    } catch (CancellationException ignored) {
                        // already noted in the log when cancelled
                    } catch (Exception e) {
                        outputLog.add("error: " + rootMessage(e));
                    }
                    capLog(outputLog);
                    pending = null;
                    runningLabel = null;
                    drained = true;                                    // show the result NOW, not on the next tick
                }

                KeyStroke key = screen.pollInput();
                if (key != null) {
                    KeyType type = key.getKeyType();
                    boolean ctrl = key.isCtrlDown() && type == KeyType.Character;
                    char cc = type == KeyType.Character ? Character.toLowerCase(key.getCharacter()) : 0;
                    if (type == KeyType.EOF) {
                        return;
                    } else if (pending != null) {
                        if (ctrl && cc == 'c') {                        // busy: only ^C (cancel) is honored
                            pending.cancel(true);
                            outputLog.add("^C — cancelled " + runningLabel);
                            pending = null;
                            runningLabel = null;
                        }
                    } else if (type == KeyType.Enter) {
                        String line = editor.text().strip();
                        editor.clear();
                        if (line.equals("exit") || line.equals("quit")) {
                            return;
                        }
                        if (!line.isEmpty()) {
                            history.add(line);
                            histIdx = history.size();
                            outputLog.add("jagt> " + line);
                            capLog(outputLog);
                            runningLabel = line;
                            runningSince = System.currentTimeMillis();
                            String cmd = line;
                            pending = worker.submit(() -> dispatch(cmd));   // off the UI thread
                        }
                    } else if (type == KeyType.ArrowUp) {
                        histIdx = recallHistory(editor, history, histIdx, -1);
                    } else if (type == KeyType.ArrowDown) {
                        histIdx = recallHistory(editor, history, histIdx, +1);
                    } else if (ctrl && cc == 'c') {
                        editor.clear();                               // ^C: abort the current line → fresh prompt
                        histIdx = history.size();
                    } else if (ctrl && cc == 'l') {
                        outputLog.clear();                            // ^L: clear the screen (scrollback log)
                    } else if (type == KeyType.Tab) {
                        completeInput(editor, outputLog);             // command / alias / ticket / flag completion
                    } else {
                        editKey(editor, key);   // arrows L/R, Home, End, Delete, Ctrl-A/E/U/K/W, printable insert
                    }
                }

                long now = System.currentTimeMillis();
                long interval = pending != null ? 120 : refreshMillis;   // animate the spinner while busy
                if (key != null || resized != null || drained || now - lastRender >= interval) {
                    lastRender = now;
                    String busy = pending == null ? null : spinner(now) + " running " + runningLabel
                            + " … " + ((now - runningSince) / 1000) + "s   (Ctrl-C to cancel)";
                    render(screen, outputLog, editor, busy, resized != null);
                }
                if (key == null) {
                    try {
                        Thread.sleep(20);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
        } finally {
            worker.shutdownNow();
        }
    }

    private static final String SPINNER = "⠋⠙⠹⠸⠼⠴⠦⠧⠇⠏";

    /** One braille spinner frame chosen from the clock, so it animates without any per-command state. */
    private static String spinner(long now) {
        return String.valueOf(SPINNER.charAt((int) ((now / 120) % SPINNER.length())));
    }

    /** Bound the output log so a long-running session can't grow it without limit. */
    private static void capLog(List<String> log) {
        if (log.size() > MAX_LOG_LINES) {
            log.subList(0, log.size() - MAX_LOG_LINES).clear();
        }
    }

    /** Deepest cause message, for showing a failed background command's reason on one line. */
    private static String rootMessage(Throwable t) {
        Throwable c = t;
        while (c.getCause() != null && c.getCause() != c) {
            c = c.getCause();
        }
        return c.getMessage() != null ? c.getMessage() : c.toString();
    }

    /**
     * Tab-complete the word at the end of the input: the command (first word), an existing task's alias/id
     * (a {@code <ticket>} arg), or a flag (`ide … diff`, `do … plan`). A single match is filled in with a
     * trailing space; several matches extend to the common prefix, or list the options (task options show
     * the title, so a bare number is recognisable). Purely local — the command set is fixed and known.
     */
    void completeInput(LineEditor editor, List<String> outputLog) {
        String[] parts = editor.text().split(" ", -1);
        int idx = parts.length - 1;
        String word = parts[idx];
        String cmd = parts[0];
        if (idx == 1 && TASK_ARG_COMMANDS.contains(cmd)) {
            completeTaskArg(editor, outputLog, parts, idx, word);
            return;
        }
        List<String> pool;
        if (idx == 0) {
            pool = COMMANDS;
        } else if (cmd.equals("ide") && idx == 2) {
            pool = List.of("diff");
        } else if (cmd.equals("do") && idx >= 2) {
            pool = List.of("plan");
        } else {
            return;
        }
        List<String> matches = pool.stream().filter(c -> c.startsWith(word)).distinct().sorted().toList();
        if (matches.size() == 1) {
            setLastToken(editor, parts, idx, matches.get(0));
        } else if (!matches.isEmpty()) {
            String common = commonPrefix(matches);
            if (!common.equals(word)) {
                parts[idx] = common;
                editor.setText(String.join(" ", parts));            // extend, no trailing space (still ambiguous)
            } else {
                outputLog.add("  " + String.join("   ", matches));  // list the options
                capLog(outputLog);
            }
        }
    }

    /** Complete a {@code <ticket>} argument from the live tasks; on ambiguity, list matches WITH titles. */
    private void completeTaskArg(LineEditor editor, List<String> outputLog, String[] parts, int idx, String word) {
        List<OrchestratorTools.TaskChoice> choices = tools.taskChoices();
        List<String> matches = choices.stream()
                .flatMap(c -> java.util.stream.Stream.of(c.alias(), c.id()))
                .filter(t -> t != null && !t.isBlank() && t.startsWith(word))
                .distinct().sorted().toList();
        if (matches.size() == 1) {
            setLastToken(editor, parts, idx, matches.get(0));
        } else if (!matches.isEmpty()) {
            String common = commonPrefix(matches);
            if (!common.equals(word)) {
                parts[idx] = common;
                editor.setText(String.join(" ", parts));
            } else {
                choices.stream()
                        .filter(c -> startsWith(c.alias(), word) || startsWith(c.id(), word))
                        .forEach(c -> outputLog.add(String.format("  %-5s %-11s %s",
                                c.alias() == null ? "-" : c.alias(), c.id(), c.title() == null ? "" : c.title())));
                capLog(outputLog);
            }
        }
    }

    private static void setLastToken(LineEditor editor, String[] parts, int idx, String token) {
        parts[idx] = token;
        editor.setText(String.join(" ", parts) + " ");   // single match → fill in, ready for the next word
    }

    private static boolean startsWith(String s, String prefix) {
        return s != null && s.startsWith(prefix);
    }

    /** Longest common prefix of a non-empty list — how far an ambiguous completion can safely fill in. */
    private static String commonPrefix(List<String> tokens) {
        String prefix = tokens.get(0);
        for (String t : tokens) {
            int n = Math.min(prefix.length(), t.length());
            int i = 0;
            while (i < n && prefix.charAt(i) == t.charAt(i)) {
                i++;
            }
            prefix = prefix.substring(0, i);
        }
        return prefix;
    }

    /**
     * Draw the whole screen into Lanterna's back buffer: output log on top, dashboard table beneath it
     * (capped so {@code commandRows} rows stay for output+input; overflow → a "… +N" line), and the input
     * line at the very bottom. NEVER {@code screen.clear()} — that forces a full terminal wipe on the next
     * refresh (visible flicker on every keystroke/tick). Instead every row is drawn (empties blanked) and
     * {@code refresh(DELTA)} sends only the cells that actually changed; {@code full} (first paint / resize)
     * uses COMPLETE.
     */
    private void render(Screen screen, List<String> outputLog, LineEditor editor, String busy, boolean full)
            throws IOException {
        TerminalSize size = screen.getTerminalSize();
        int height = size.getRows();
        int width = size.getColumns();
        TextGraphics g = screen.newTextGraphics();

        String[] dash = dashboard.render().split("\\R");
        int body = Math.max(1, height - 1);                       // rows above the input line

        // Expand the dashboard: wrap a long task title onto continuation lines indented under the TITLE
        // column, so the whole title is visible; every other line passes through unchanged.
        int titleAvail = Math.max(1, width - DashboardRenderer.COL_TITLE);
        String indent = " ".repeat(DashboardRenderer.COL_TITLE);
        List<DashRow> drows = new ArrayList<>();
        for (String line : dash) {
            if (isTaskRow(line) && line.length() > DashboardRenderer.COL_TITLE) {
                String head = line.substring(0, DashboardRenderer.COL_TITLE);
                List<String> parts = wrap(line.substring(DashboardRenderer.COL_TITLE), titleAvail);
                drows.add(new DashRow(head + parts.get(0), RowKind.TASK));
                for (int p = 1; p < parts.size(); p++) {
                    drows.add(new DashRow(indent + parts.get(p), RowKind.TITLE_CONT));
                }
            } else if (!isTaskRow(line) && line.length() > width) {
                // A detail (└) or next-move (→) line longer than the screen would otherwise be CLIPPED,
                // hiding its tail. Wrap it with a hanging indent so the whole line is visible at any width,
                // continuations aligned under the text after the marker (and kept yellow for a "your move").
                RowKind cont = dashColor(line) == TextColor.ANSI.YELLOW_BRIGHT ? RowKind.MOVE_CONT : RowKind.PLAIN;
                List<String> parts = wrapHanging(line, width);
                drows.add(new DashRow(parts.get(0), RowKind.PLAIN));
                for (int p = 1; p < parts.size(); p++) {
                    drows.add(new DashRow(parts.get(p), cont));
                }
            } else {
                drows.add(new DashRow(line, isTaskRow(line) ? RowKind.TASK : RowKind.PLAIN));
            }
        }
        int dashRows = Math.min(drows.size(), Math.max(1, body - commandRows));
        int outputRows = body - dashRows;                          // output log gets whatever is left on top
        int dashTop = outputRows;

        // Word-wrap the log so long lines (paths, URLs) show in full instead of truncating. Build bottom-up
        // and stop once we have enough to fill outputRows — don't wrap the whole 2000-line history each frame.
        List<Row> rows = new ArrayList<>();
        for (int e = outputLog.size() - 1; e >= 0 && rows.size() < outputRows; e--) {
            String entry = outputLog.get(e);
            // A colored command echo ("jagt> …") heads each block — that IS the visual separator; errors red.
            TextColor color = entry.startsWith("jagt> ") ? TextColor.ANSI.CYAN_BRIGHT
                    : entry.startsWith("error:") ? TextColor.ANSI.RED_BRIGHT : TextColor.ANSI.DEFAULT;
            boolean bold = entry.startsWith("jagt> ");
            List<String> segs = wrap(entry, width);
            for (int s = segs.size() - 1; s >= 0; s--) {           // add segments bottom-up
                rows.add(new Row(segs.get(s), color, bold));
            }
        }
        for (int y = 0; y < outputRows; y++) {                     // newest at the bottom row, blanks above
            int fromBottom = outputRows - 1 - y;
            Row r = fromBottom < rows.size() ? rows.get(fromBottom) : null;
            if (r != null) {
                put(g, y, r.text(), width, r.color(), r.bold());
            } else {
                put(g, y, "", width, TextColor.ANSI.DEFAULT, false);
            }
        }
        // The alias, ticket and title all share ONE colour (distinct from the shell's cyan prompt) so a
        // task's identity reads as one unit; status/project/active stay plain.
        TextColor idColor = TextColor.ANSI.MAGENTA_BRIGHT;
        for (int i = 0; i < dashRows; i++) {
            int y = dashTop + i;
            if (drows.size() > dashRows && i == dashRows - 1) {    // more rows than fit → collapse the tail
                put(g, y, "  … +" + (drows.size() - dashRows + 1) + " more — see all: curl localhost:8290/status",
                        width, TextColor.ANSI.YELLOW_BRIGHT, false);
                continue;
            }
            DashRow dr = drows.get(i);
            switch (dr.kind()) {
                case TASK -> {
                    put(g, y, dr.text(), width, TextColor.ANSI.DEFAULT, false);   // base (status/project/active)
                    colorSpan(g, y, dr.text(), DashboardRenderer.COL_ALIAS, DashboardRenderer.ALIAS_W, idColor, width);
                    colorSpan(g, y, dr.text(), DashboardRenderer.COL_TASK, DashboardRenderer.TASK_W, idColor, width);
                    colorSpan(g, y, dr.text(), DashboardRenderer.COL_TITLE, width, idColor, width);
                }
                case TITLE_CONT -> put(g, y, dr.text(), width, idColor, true);
                case MOVE_CONT -> put(g, y, dr.text(), width, TextColor.ANSI.YELLOW_BRIGHT, false);
                case PLAIN -> put(g, y, dr.text(), width, dashColor(dr.text()), dr.text().startsWith("jagt orchestrator"));
            }
        }
        if (busy != null) {                                        // a command is running: spinner, no prompt
            put(g, height - 1, busy, width, TextColor.ANSI.YELLOW_BRIGHT, true);
            screen.setCursorPosition(null);                        // hide the cursor while busy
        } else {
            String text = editor.text();
            int avail = Math.max(1, width - 6);                    // columns available for the typed text
            int cur = editor.cursor();
            int start = cur >= avail ? cur - avail + 1 : 0;        // horizontal scroll to keep the cursor visible
            String visible = text.substring(Math.min(start, text.length()), Math.min(text.length(), start + avail));
            put(g, height - 1, "jagt> ", width, TextColor.ANSI.CYAN_BRIGHT, true); // input line: prompt…
            g.putString(6, height - 1, fit(visible, avail));                       // …then the (scrolled) text
            screen.setCursorPosition(new TerminalPosition(6 + (cur - start), height - 1));
        }
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

    /** A dashboard task row (has an alias in column 0) — not the header, column header, overflow or details. */
    private static boolean isTaskRow(String text) {
        return !text.isEmpty() && text.charAt(0) != ' '
                && !text.startsWith("jagt orchestrator")
                && !text.startsWith("ALIAS")
                && !text.startsWith("(no tasks)");
    }

    /** Recolour (bold) the column [start, start+len) of an already-drawn row, clipped to the line and width. */
    private static void colorSpan(TextGraphics g, int row, String line, int start, int len, TextColor color,
                                  int width) {
        int end = Math.min(line.length(), Math.min(width, start + len));
        if (start >= end) {
            return;
        }
        g.setForegroundColor(color);
        g.enableModifiers(SGR.BOLD);
        g.putString(start, row, line.substring(start, end));
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

    /** One coloured, wrapped output-log display line. */
    private record Row(String text, TextColor color, boolean bold) { }

    /** How a dashboard display row is coloured: header/detail line, task row, wrapped-title tail, or a
     *  wrapped next-move tail (kept yellow so a "your move" reads as one highlighted unit across wraps). */
    private enum RowKind { PLAIN, TASK, TITLE_CONT, MOVE_CONT }

    private record DashRow(String text, RowKind kind) { }

    /**
     * Word-wrap {@code s} to {@code width} columns: break at the last space before the limit, or hard-break
     * a token (long path/URL) that has no space. Returns the single line unchanged when it already fits.
     */
    static List<String> wrap(String s, int width) {
        if (width <= 0 || s.length() <= width) {
            return List.of(s);
        }
        List<String> out = new ArrayList<>();
        int i = 0;
        while (i < s.length()) {
            int end = Math.min(s.length(), i + width);
            if (end < s.length()) {
                int space = s.lastIndexOf(' ', end);
                if (space > i) {
                    end = space;                       // break at a word boundary when there is one
                }
            }
            out.add(s.substring(i, end));
            i = end;
            while (i < s.length() && s.charAt(i) == ' ') {
                i++;                                   // drop the space we broke on
            }
        }
        return out;
    }

    /**
     * Word-wrap a dashboard detail line ({@code   └ <url>} / {@code   → <move>}) to {@code width}, HANGING
     * the continuations under the text (past the leading indent + {@code └}/{@code →} marker) so a wrapped
     * line still reads as one indented item. Only the first visual line carries the marker.
     */
    static List<String> wrapHanging(String line, int width) {
        if (width <= 0 || line.length() <= width) {
            return List.of(line);
        }
        int hang = hangingIndent(line);
        if (hang >= width) {
            hang = 0;                                  // pathologically narrow terminal: fall back to a plain wrap
        }
        List<String> pieces = wrap(line.substring(hang), width - hang);
        List<String> out = new ArrayList<>();
        out.add(line.substring(0, hang) + pieces.get(0));
        String pad = " ".repeat(hang);
        for (int p = 1; p < pieces.size(); p++) {
            out.add(pad + pieces.get(p));
        }
        return out;
    }

    /** Columns to indent a wrapped detail line's continuations: its leading spaces, plus the {@code └}/{@code →}
     *  marker and the space after it when present, so continuations align under the text, not the marker. */
    static int hangingIndent(String line) {
        int i = 0;
        while (i < line.length() && line.charAt(i) == ' ') {
            i++;
        }
        if (i < line.length() && (line.charAt(i) == '└' || line.charAt(i) == '→')) {
            i++;
            if (i < line.length() && line.charAt(i) == ' ') {
                i++;
            }
        }
        return i;
    }

    /**
     * Apply one non-Enter editing keystroke to the input editor: cursor movement (arrows, Home/End,
     * Ctrl-Left/Right by word), deletion (Backspace/Delete, Ctrl-U/K to line start/end, Ctrl-W a word),
     * else a printable character is inserted at the cursor. (Enter, history arrows and Ctrl-C/L are
     * handled by the loop, which owns the command/history/log state.)
     */
    private static void editKey(LineEditor e, KeyStroke k) {
        switch (k.getKeyType()) {
            case Backspace -> e.backspace();
            case Delete -> e.delete();
            case ArrowLeft -> { if (k.isCtrlDown()) e.wordLeft(); else e.left(); }
            case ArrowRight -> { if (k.isCtrlDown()) e.wordRight(); else e.right(); }
            case Home -> e.home();
            case End -> e.end();
            case Character -> {
                char c = k.getCharacter();
                if (k.isCtrlDown()) {
                    switch (Character.toLowerCase(c)) {
                        case 'a' -> e.home();
                        case 'e' -> e.end();
                        case 'u' -> e.killToStart();
                        case 'k' -> e.killToEnd();
                        case 'w' -> e.deleteWordBack();
                        default -> { }
                    }
                } else {
                    e.insert(c);
                }
            }
            default -> { }
        }
    }

    /** Move {@code dir} (-1 up / +1 down) through {@code history}, loading it into {@code e}; returns the new
     *  index. Past the newest entry the line goes empty (== {@code history.size()}), like a real shell. */
    private static int recallHistory(LineEditor e, List<String> history, int idx, int dir) {
        int n = idx + dir;
        if (n < 0 || history.isEmpty()) {
            return idx;
        }
        if (n >= history.size()) {
            e.clear();
            return history.size();
        }
        e.setText(history.get(n));
        return n;
    }

    /** A minimal single-line editor: a buffer plus a cursor, with the ops a real prompt supports. Pure
     *  logic (no terminal), so it is unit-tested directly. */
    static final class LineEditor {
        private final StringBuilder buf = new StringBuilder();
        private int cursor;

        String text() {
            return buf.toString();
        }

        int cursor() {
            return cursor;
        }

        void insert(char c) {
            buf.insert(cursor++, c);
        }

        void setText(String s) {
            buf.setLength(0);
            buf.append(s);
            cursor = buf.length();
        }

        void clear() {
            buf.setLength(0);
            cursor = 0;
        }

        void backspace() {
            if (cursor > 0) {
                buf.deleteCharAt(--cursor);
            }
        }

        void delete() {
            if (cursor < buf.length()) {
                buf.deleteCharAt(cursor);
            }
        }

        void left() {
            if (cursor > 0) {
                cursor--;
            }
        }

        void right() {
            if (cursor < buf.length()) {
                cursor++;
            }
        }

        void home() {
            cursor = 0;
        }

        void end() {
            cursor = buf.length();
        }

        void killToStart() {
            buf.delete(0, cursor);
            cursor = 0;
        }

        void killToEnd() {
            buf.setLength(cursor);
        }

        void deleteWordBack() {
            int i = wordBoundaryBefore(cursor);
            buf.delete(i, cursor);
            cursor = i;
        }

        void wordLeft() {
            cursor = wordBoundaryBefore(cursor);
        }

        void wordRight() {
            int i = cursor;
            int n = buf.length();
            while (i < n && Character.isWhitespace(buf.charAt(i))) {
                i++;
            }
            while (i < n && !Character.isWhitespace(buf.charAt(i))) {
                i++;
            }
            cursor = i;
        }

        /** Start of the word at/just before {@code pos}: skip trailing spaces, then the word. */
        private int wordBoundaryBefore(int pos) {
            int i = pos;
            while (i > 0 && Character.isWhitespace(buf.charAt(i - 1))) {
                i--;
            }
            while (i > 0 && !Character.isWhitespace(buf.charAt(i - 1))) {
                i--;
            }
            return i;
        }
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
        String ref = arg(tok, 1, "do <ticket|url> [project] [plan] [notes…]");
        DoArgs a = parseDoArgs(tok);
        boolean bareKey = KEY_REF.matcher(ref).matches();

        // Warn before spending a ticket read on a task that would only collide later; a chosen strategy
        // means the collision is intended, so let it through.
        if (bareKey && a.strategy == null) {
            String existing = tools.existingBranchProject(ref, a.project);
            if (existing != null) {
                return "branch '" + ref + "' already exists in " + existing + " (previous run of this"
                        + " ticket). Retry with `do " + ref + " recreate` (discard old work, start fresh)"
                        + " or `do " + ref + " resume` (continue its commits).";
            }
        }

        // Fast path: a bare key + explicit project needs no read — the key IS the task id.
        if (bareKey && a.project != null) {
            return tools.initializeTask(ref, resolveProject(a.project),
                    withNotes("Read " + ref + " via your issue-tracker MCP and implement it.", a.notes),
                    a.mode, a.strategy, null, null);
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
            String project = a.project != null ? resolveProject(a.project) : resolveByLabels(f);
            String instructions = withNotes("Implement " + taskId + " — \"" + f.title()
                    + "\". Read it via your issue-tracker MCP for full details, then work.", a.notes);
            return tools.initializeTask(taskId, project, instructions, a.mode, a.strategy, f.title(), f.url());
        }
        // Assistant unavailable: only a bare key can proceed — a URL has no derivable task id without it.
        if (!bareKey) {
            return "error: assistant unavailable — pass an issue key (not a URL), or add the project";
        }
        return tools.initializeTask(ref, resolveProject(a.project),
                withNotes("Read " + ref + " via your issue-tracker MCP and implement it.", a.notes),
                a.mode, a.strategy, null, null);
    }

    record DoArgs(String project, String mode, String strategy, String notes) {
    }

    private static final Set<String> BRANCH_STRATEGIES = Set.of("recreate", "resume", "fresh");

    /**
     * Splits {@code do <ticket> …} after the ticket: leading {@code plan}, a known project key, and a branch
     * strategy — in any order — are consumed as modifiers; everything after them is free-text notes. Each
     * modifier is recognised only as a leading token, so a note may contain the word "plan".
     */
    DoArgs parseDoArgs(List<String> tok) {
        List<String> rest = new ArrayList<>(tok.subList(Math.min(2, tok.size()), tok.size()));
        Set<String> projectKeys = configService.load().projects().keySet();
        String mode = null;
        String project = null;
        String strategy = null;
        while (!rest.isEmpty()) {
            String head = rest.get(0);
            if (mode == null && head.equals("plan")) {
                mode = "plan";
            } else if (project == null && projectKeys.contains(head)) {
                project = head;
            } else if (strategy == null && BRANCH_STRATEGIES.contains(head)) {
                strategy = head;
            } else {
                break;
            }
            rest.remove(0);
        }
        return new DoArgs(project, mode, strategy, String.join(" ", rest).strip());
    }

    private static String withNotes(String instructions, String notes) {
        return notes == null || notes.isBlank()
                ? instructions
                : instructions + "\n\nAdditional instructions from the human:\n" + notes;
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
        return reviewSweep.sweep(arg(tok, 1, "review <ticket>")).message();
    }

    /** Picks the jagt project whose configured labels intersect the ticket's labels (or tracker project key). */
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

    /** Pure: the project keys whose labels intersect the ticket's labels or its tracker project key. */
    static List<String> projectsMatching(TicketFacts f, Map<String, List<String>> projectLabels) {
        Set<String> tokens = new HashSet<>(f.labels());
        tokens.add(f.trackerProject());
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
