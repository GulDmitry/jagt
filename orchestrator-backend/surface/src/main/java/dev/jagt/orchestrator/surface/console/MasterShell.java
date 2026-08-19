package dev.jagt.orchestrator.surface.console;

import dev.jagt.orchestrator.flow.TaskAction;
import dev.jagt.orchestrator.task.TaskChoice;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.DashboardRenderer;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.command.StateViews;
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
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.Set;
import java.util.stream.Stream;

@Component
@Slf4j
@RequiredArgsConstructor
public class MasterShell {

    private final StateViews views;
    private final ConfigService configService;
    private final StateService stateService;
    private final GrammarDispatch grammar;
    private final ConfigurableApplicationContext context;

    /** Rows kept for output + input, so a long task list cannot eat the whole screen. */
    private int commandRows = 17;

    /** Set by the state-change listener, consumed by the render loop: one repaint per batch of changes. */
    private final AtomicBoolean stateDirty = new AtomicBoolean();

    private static final int MAX_LOG_LINES = 2000;

    /** The shell's own words, which the dispatch never sees — stopping the process is not a command it runs. */
    private static final List<String> SHELL_COMMANDS = List.of("quit", "exit");

    /** Commands whose first argument is an EXISTING task, so Tab completes its alias/id. */
    private static final Set<String> TASK_ARG_COMMANDS = java.util.Arrays.stream(TaskAction.values())
            .flatMap(action -> Stream.concat(Stream.of(action.id()), action.retiredVerbs().stream()))
            .collect(java.util.stream.Collectors.toUnmodifiableSet());

    public void run() {
        ConfigService.ConfigFile config = configService.load();
        int refreshSeconds = config.dashboard().refreshSecondsOrDefault();
        this.commandRows = config.dashboard().reservedRowsOrDefault();
        // Only a FLAG: the screen belongs to the UI thread, and this runs on whichever thread served the
        // agent's MCP call.
        stateService.onChange(state -> stateDirty.set(true));

        Screen screen = null;
        try {
            // No TTY (Gradle pipes stdout) means a garbled TUI, so fall back to a plain line REPL.
            if (System.console() == null) {
                runInlineFallback();
            } else {
                Terminal terminal = new DefaultTerminalFactory()
                        // Or Lanterna opens a Swing window when a GUI is present.
                        .setForceTextTerminal(true)
                        // Ctrl-C arrives as a keystroke (abort the line) instead of killing the JVM.
                        .setUnixTerminalCtrlCBehaviour(UnixLikeTerminal.CtrlCBehaviour.TRAP)
                        .createTerminal();
                screen = new TerminalScreen(terminal);
                screen.startScreen();
                // 0 or less = no periodic refresh: redraw only on input/resize.
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
                } catch (Throwable t) {                           // best-effort — a corrupted jar may fail here
                    log.debug("screen stop failed: {}", t.toString());
                }
            }
            shutdownBackend();
        }
    }

    /**
     * Stop the backend and GUARANTEE the process dies. A jar rewritten under a running JVM can make
     * {@code close()} throw or hang, leaving non-daemon threads alive, so it runs on a capped side thread and
     * {@code halt(0)} follows either way — it skips shutdown hooks and loads no class. Agents live in tmux.
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
     * One back-buffer: output log, dashboard, input line. Every frame is drawn from scratch after
     * {@code doResizeIfNecessary}, which is what makes a resize a re-layout instead of a ghost.
     */
    private void runTui(Screen screen, long refreshMillis) throws IOException {
        List<String> outputLog = new ArrayList<>();
        outputLog.add("jagt — type a command ('help'); 'exit' stops the backend (agents keep running).");
        LineEditor editor = new LineEditor();
        List<String> history = new ArrayList<>();
        int histIdx = 0;                                           // navigation cursor; == size() means "new line"
        // A command can take tens of seconds; on the UI thread, Enter would freeze the screen for all of it.
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
                if (pending != null && pending.isDone()) {
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
                        if (ctrl && cc == 'c') {
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
                            pending = worker.submit(() -> grammar.run(cmd));
                        }
                    } else if (type == KeyType.ArrowUp) {
                        histIdx = recallHistory(editor, history, histIdx, -1);
                    } else if (type == KeyType.ArrowDown) {
                        histIdx = recallHistory(editor, history, histIdx, +1);
                    } else if (ctrl && cc == 'c') {
                        editor.clear();
                        histIdx = history.size();
                    } else if (ctrl && cc == 'l') {
                        outputLog.clear();
                    } else if (type == KeyType.Tab) {
                        completeInput(editor, outputLog);
                    } else {
                        editKey(editor, key);
                    }
                }

                long now = System.currentTimeMillis();
                long interval = pending != null ? 120 : refreshMillis;   // animate the spinner while busy
                boolean stateChanged = stateDirty.getAndSet(false);
                if (key != null || resized != null || drained || stateChanged || now - lastRender >= interval) {
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

    private static void capLog(List<String> log) {
        if (log.size() > MAX_LOG_LINES) {
            log.subList(0, log.size() - MAX_LOG_LINES).clear();
        }
    }

    private static String rootMessage(Throwable t) {
        Throwable c = t;
        while (c.getCause() != null && c.getCause() != c) {
            c = c.getCause();
        }
        return c.getMessage() != null ? c.getMessage() : c.toString();
    }

    void completeInput(LineEditor editor, List<String> outputLog) {
        String[] parts = editor.text().split(" ", -1);
        int idx = parts.length - 1;
        String word = parts[idx];
        // The dispatch reads a verb case-insensitively, so completion has to as well: `Review p<Tab>` doing
        // nothing while `Review p1` runs is the completer disagreeing with the grammar.
        String cmd = parts[0].toLowerCase(java.util.Locale.ROOT);
        if (idx == 1 && TASK_ARG_COMMANDS.contains(cmd)) {
            completeTaskArg(editor, outputLog, parts, idx, word);
            return;
        }
        List<String> pool;
        if (idx == 0) {
            pool = Stream.concat(grammar.completions().stream(), SHELL_COMMANDS.stream()).toList();
        } else if (cmd.equals("ide") && idx == 2) {
            pool = List.of("diff");
        } else if (cmd.equals("do") && idx >= 2) {
            pool = List.of("plan", "from");
        } else {
            return;
        }
        String typed = word.toLowerCase(java.util.Locale.ROOT);
        List<String> matches = pool.stream().filter(c -> c.startsWith(typed)).distinct().sorted().toList();
        if (matches.size() == 1) {
            setLastToken(editor, parts, idx, matches.get(0));
        } else if (!matches.isEmpty()) {
            String common = commonPrefix(matches);
            if (!common.equals(word)) {
                parts[idx] = common;
                editor.setText(String.join(" ", parts));            // extend, no trailing space (still ambiguous)
            } else {
                outputLog.add("  " + String.join("   ", matches));
                capLog(outputLog);
            }
        }
    }

    private void completeTaskArg(LineEditor editor, List<String> outputLog, String[] parts, int idx, String word) {
        List<TaskChoice> choices = views.taskChoices();
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
     * Draws every row into the back buffer, blanks included. NEVER {@code screen.clear()}: it wipes the
     * terminal on the next refresh, which is visible flicker on every keystroke. {@code refresh(DELTA)} sends
     * only changed cells; a first paint or resize needs COMPLETE.
     */
    private void render(Screen screen, List<String> outputLog, LineEditor editor, String busy, boolean full)
            throws IOException {
        TerminalSize size = screen.getTerminalSize();
        int height = size.getRows();
        int width = size.getColumns();
        TextGraphics g = screen.newTextGraphics();

        String[] dash = views.dashboard().split("\\R");
        int body = Math.max(1, height - 1);                       // rows above the input line

        // A long title wraps under the TITLE column instead of being cut.
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
                // Clipping a detail or next-move line hides its tail, so it wraps with a hanging indent.
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
        int outputRows = body - dashRows;
        int dashTop = outputRows;

        // Bottom-up and stop at outputRows: wrapping the whole 2000-line history every frame is waste.
        List<Row> rows = new ArrayList<>();
        for (int e = outputLog.size() - 1; e >= 0 && rows.size() < outputRows; e--) {
            String entry = outputLog.get(e);
            // A colored command echo ("jagt> …") heads each block — that IS the visual separator; errors red.
            TextColor color = entry.startsWith("jagt> ") ? TextColor.ANSI.CYAN_BRIGHT
                    : entry.startsWith("error:") ? TextColor.ANSI.RED_BRIGHT : TextColor.ANSI.DEFAULT;
            boolean bold = entry.startsWith("jagt> ");
            List<String> segs = wrap(entry, width);
            for (int s = segs.size() - 1; s >= 0; s--) {
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
            if (drows.size() > dashRows && i == dashRows - 1) {
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
            g.putString(6, height - 1, fit(visible, avail));
            screen.setCursorPosition(new TerminalPosition(6 + (cur - start), height - 1));
        }
        screen.refresh(full ? Screen.RefreshType.COMPLETE : Screen.RefreshType.DELTA);
    }

    private static TextColor dashColor(String text) {
        if (text.startsWith("jagt orchestrator")) {
            return TextColor.ANSI.GREEN_BRIGHT;
        }
        if (text.contains("your move") || text.contains("… +")) {
            return TextColor.ANSI.YELLOW_BRIGHT;
        }
        return TextColor.ANSI.DEFAULT;
    }

    private static void put(TextGraphics g, int row, String text, int width, TextColor color, boolean bold) {
        g.setForegroundColor(color);
        if (bold) {
            g.enableModifiers(SGR.BOLD);
        }
        g.putString(0, row, fit(text, width));
        g.disableModifiers(SGR.BOLD);
        g.setForegroundColor(TextColor.ANSI.DEFAULT);
    }

    private static boolean isTaskRow(String text) {
        return !text.isEmpty() && text.charAt(0) != ' '
                && !text.startsWith("jagt orchestrator")
                && !text.startsWith("ALIAS")
                && !text.startsWith("(no tasks)");
    }

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

    private record Row(String text, TextColor color, boolean bold) { }

    /** A wrapped next-move tail stays yellow, so a "your move" reads as one highlighted unit across wraps. */
    private enum RowKind { PLAIN, TASK, TITLE_CONT, MOVE_CONT }

    private record DashRow(String text, RowKind kind) { }

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
     * The continuations hang under the TEXT, past the {@code └}/{@code →} marker, so a wrapped line still reads
     * as one indented item.
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
     * Enter, the history arrows and Ctrl-C/L are NOT handled here: they belong to the loop, which owns the
     * command, history and log state.
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

    /** {@code dir} is -1 up / +1 down. Past the newest entry the line goes empty, like a real shell. */
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

    /** No-TTY fallback ({@code gradlew bootRun} pipes stdout): a plain line REPL, with no terminal to draw on. */
    private void runInlineFallback() {
        log.info("No interactive terminal — Master shell running inline (dashboard after each command).");
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        System.out.println(withDashboard("", views.dashboard()));
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
                System.out.println(withDashboard(grammar.run(cmd), views.dashboard()));
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

    static String withDashboard(String result, String dashboardText) {
        return result.isBlank() ? dashboardText : result + "\n\n" + dashboardText;
    }
}
