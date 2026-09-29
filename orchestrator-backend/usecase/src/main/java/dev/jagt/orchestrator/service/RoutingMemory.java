package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What places an item that no finished task can teach — a rule written before the first item of its kind
 * arrives. A line, not a row: a human opens the file, reads every rule at once and edits the wrong one, which
 * is what a store nobody can read never allows.
 *
 * <p>One rule per KEY, the newest winning, so a rule that changed replaces the one it changed rather than
 * arguing with it in the same file. Past its ceiling the oldest goes: a file nothing ever drops ends up
 * teaching a layout the install left behind.
 */
@Component
@Slf4j
public class RoutingMemory {

    /** Kept whole in every routing prompt, so the ceiling is what a prompt can carry without drowning the item. */
    private static final int MAX_RULES = 60;
    private static final String FILE = "memory/routing.md";
    private static final String SEPARATOR = " -> ";

    private final OrchestratorPaths paths;
    private final int maxRules;
    private final Clock clock;

    @Autowired
    public RoutingMemory(OrchestratorPaths paths) {
        this(paths, MAX_RULES);
    }

    RoutingMemory(OrchestratorPaths paths, int maxRules) {
        this(paths, maxRules, Clock.systemDefaultZone());
    }

    RoutingMemory(OrchestratorPaths paths, int maxRules, Clock clock) {
        this.paths = paths;
        this.maxRules = maxRules;
        this.clock = clock;
    }

    /** Every rule the install holds, oldest first; empty where nobody has written one. */
    public List<String> rules() {
        return byKey().entrySet().stream().map(entry -> entry.getKey() + SEPARATOR + entry.getValue()).toList();
    }

    /**
     * Writes {@code key} placing work in {@code project}, replacing whatever that key said before. Answers
     * whether the file changed, so a caller can say what it did without reading it back. Serialised: a job and
     * a typed launch write the same file, and read-modify-write from both loses one of them.
     */
    public synchronized boolean remember(String key, String project) {
        if (key == null || key.isBlank() || project == null || project.isBlank()) {
            return false;
        }
        Map<String, String> rules = byKey();
        String cleaned = key.strip().replace('\n', ' ');
        String stood = rules.get(cleaned);
        if (project.strip().equals(stood)) {
            return false;
        }
        rules.remove(cleaned);
        rules.put(cleaned, project.strip());
        // A rule that stopped being true is kept, dated: only the retired line answers why a task went where
        // it went last quarter, and a line silently gone answers nothing.
        List<String> retired = new ArrayList<>(retired());
        if (stood != null) {
            retired.add("# until " + LocalDate.now(clock) + ": " + cleaned + SEPARATOR + stood);
        }
        write(rules, retired);
        return true;
    }

    /** The dated lines of rules that stopped being true, oldest first. */
    private List<String> retired() {
        return lines().stream().map(String::strip).filter(line -> line.startsWith("# until ")).toList();
    }

    /**
     * Takes a rule out of use, keeping it dated. {@code rendered} is a line as {@link #rules()} hands it out,
     * so a caller never has to know how a rule is spelled on disk.
     */
    public synchronized boolean retire(String rendered) {
        if (rendered == null) {
            return false;
        }
        int split = rendered.lastIndexOf(SEPARATOR);
        if (split < 0) {
            return false;
        }
        String key = rendered.substring(0, split).strip();
        Map<String, String> rules = byKey();
        String stood = rules.remove(key);
        if (stood == null) {
            return false;
        }
        List<String> retired = new ArrayList<>(retired());
        retired.add("# until " + LocalDate.now(clock) + ": " + key + SEPARATOR + stood);
        write(rules, retired);
        return true;
    }

    private Map<String, String> byKey() {
        Map<String, String> rules = new LinkedHashMap<>();
        for (String line : lines()) {
            String read = line.strip();
            if (read.isEmpty() || read.startsWith("#")) {
                continue;
            }
            int split = read.lastIndexOf(SEPARATOR);
            if (split < 0) {
                log.atWarn().setMessage("routing rule unreadable")
                        .addKeyValue("file", file())
                        .addKeyValue("cause", "no '" + SEPARATOR.strip() + "' in: " + read)
                        .log();
                continue;
            }
            rules.put(read.substring(0, split).strip(), read.substring(split + SEPARATOR.length()).strip());
        }
        return rules;
    }

    private List<String> lines() {
        Path file = file();
        if (!Files.isRegularFile(file)) {
            return List.of();
        }
        try {
            return Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException unreadable) {
            // Read rather than missing: an install whose memory cannot be opened must not look like one holding none.
            log.atError().setMessage("routing memory unreadable")
                    .addKeyValue("file", file)
                    .addKeyValue("cause", unreadable.toString())
                    .log();
            return List.of();
        }
    }

    private void write(Map<String, String> rules, List<String> retired) {
        List<String> kept = new ArrayList<>(rules.entrySet().stream()
                .skip(Math.max(0, rules.size() - maxRules))
                .map(entry -> entry.getKey() + SEPARATOR + entry.getValue())
                .toList());
        // The history is bounded too: a file nothing ever drops is one nobody opens.
        retired.stream().skip(Math.max(0, retired.size() - maxRules)).forEach(kept::add);
        Path file = file();
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, String.join("\n", kept) + "\n", StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException unwritable) {
            log.atError().setMessage("routing memory unwritable")
                    .addKeyValue("file", file)
                    .addKeyValue("cause", unwritable.toString())
                    .log();
        }
    }

    private Path file() {
        return paths.root().resolve(FILE);
    }
}
