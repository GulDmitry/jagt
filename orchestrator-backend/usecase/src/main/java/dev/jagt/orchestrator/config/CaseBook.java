package dev.jagt.orchestrator.config;

import dev.jagt.orchestrator.port.Problem;
import dev.jagt.orchestrator.task.ActionOrigin;
import dev.jagt.orchestrator.task.TaskStatus;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** Which step may be sent to a session at which status. */
public record CaseBook(List<Case> cases, List<Problem> faults) {

    public enum By {
        JAGT, MASTER, HUMAN;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        static Optional<By> of(String id) {
            return Arrays.stream(values()).filter(by -> by.id().equals(id)).findFirst();
        }

        /** Only the Master's session reaches a verb over MCP. */
        public static By of(@Nullable ActionOrigin origin) {
            if (origin == null) {
                return JAGT;
            }
            return switch (origin) {
                case BOARD, PALETTE -> HUMAN;
                case MASTER, MCP -> MASTER;
                case AUTO_REVIEW, TRACKER -> JAGT;
            };
        }
    }

    public record Case(String file, String id, String step, Set<TaskStatus> at, By by) {
    }

    private static final Pattern KEY = Pattern.compile("(step|at|by):\\s*(.*)");
    private static final String ANY = "any";
    private static final String ANY_BUT = "any but ";

    public CaseBook {
        cases = List.copyOf(cases);
        faults = List.copyOf(faults);
    }

    public static CaseBook empty() {
        return new CaseBook(List.of(), List.of());
    }

    public static CaseBook parse(String file, String text) {
        List<Case> cases = new ArrayList<>();
        List<Problem> faults = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Sections.Section section : Sections.of(text)) {
            if (!seen.add(section.id())) {
                faults.add(fault(file, section.id(), "is written twice"));
                continue;
            }
            read(file, section, faults).ifPresent(cases::add);
        }
        return new CaseBook(cases, faults);
    }

    /** This book's cases replace the shipped one's of the same id; the rest of the shipped book stands. */
    public CaseBook over(CaseBook shipped) {
        Map<String, Case> merged = new LinkedHashMap<>();
        shipped.cases.forEach(c -> merged.put(c.id(), c));
        cases.forEach(c -> merged.put(c.id(), c));
        return new CaseBook(List.copyOf(merged.values()),
                Stream.concat(faults.stream(), shipped.faults.stream()).toList());
    }

    public boolean sends(String step, TaskStatus status) {
        return cases.stream().anyMatch(c -> c.step().equals(step) && c.at().contains(status));
    }

    /** What keeps this book from matching {@code steps}, both ways, beside what it could not read. */
    public List<Problem> problems(Set<String> steps, String file) {
        List<Problem> problems = new ArrayList<>(faults);
        cases.stream().filter(c -> !steps.contains(c.step()))
                .forEach(c -> problems.add(fault(c.file(), c.id(), "names step '" + c.step()
                        + "', which no brief section holds")));
        steps.stream().filter(step -> cases.stream().noneMatch(c -> c.step().equals(step)))
                .forEach(step -> problems.add(new Problem(file, file + ": no case sends the brief step '" + step
                        + "' — add one, or it never reaches a session")));
        return List.copyOf(problems);
    }

    private static Optional<Case> read(String file, Sections.Section section, List<Problem> faults) {
        Map<String, String> keys = new LinkedHashMap<>();
        List<String> says = new ArrayList<>();
        section.body().lines().map(String::strip).filter(line -> !line.isEmpty()).forEach(line -> {
            Matcher key = KEY.matcher(line);
            if (key.matches()) {
                keys.put(key.group(1), key.group(2).strip());
            } else {
                says.add(line);
            }
        });
        int before = faults.size();
        String id = section.id();
        String step = keys.getOrDefault("step", "");
        if (step.isEmpty()) {
            faults.add(fault(file, id, "names no step"));
        }
        Optional<Set<TaskStatus>> at = statuses(keys.getOrDefault("at", ""));
        if (at.isEmpty()) {
            faults.add(fault(file, id, "has at: '" + keys.getOrDefault("at", "")
                    + "', which is not any, any but <statuses>, or a list of " + Arrays.toString(TaskStatus.values())));
        }
        Optional<By> by = By.of(keys.getOrDefault("by", ""));
        if (by.isEmpty()) {
            faults.add(fault(file, id, "has by: '" + keys.getOrDefault("by", "") + "', which is not jagt, master or human"));
        }
        if (says.isEmpty()) {
            faults.add(fault(file, id, "says nothing of what it is"));
        }
        return faults.size() > before ? Optional.empty()
                : Optional.of(new Case(file, id, step, at.orElseThrow(), by.orElseThrow()));
    }

    private static Optional<Set<TaskStatus>> statuses(String at) {
        if (at.equals(ANY)) {
            return Optional.of(EnumSet.allOf(TaskStatus.class));
        }
        boolean but = at.startsWith(ANY_BUT);
        Optional<Set<TaskStatus>> named = named(but ? at.substring(ANY_BUT.length()) : at);
        return but ? named.map(excluded -> EnumSet.complementOf(EnumSet.copyOf(excluded))) : named;
    }

    private static Optional<Set<TaskStatus>> named(String list) {
        Set<TaskStatus> statuses = EnumSet.noneOf(TaskStatus.class);
        for (String name : list.split(",")) {
            Optional<TaskStatus> status = Arrays.stream(TaskStatus.values())
                    .filter(value -> value.name().equals(name.strip())).findFirst();
            if (status.isEmpty()) {
                return Optional.empty();
            }
            statuses.add(status.get());
        }
        return Optional.of(statuses);
    }

    private static Problem fault(String file, String id, String what) {
        return new Problem(file, file + ": case " + id + " " + what);
    }
}
