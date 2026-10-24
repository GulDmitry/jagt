package dev.jagt.orchestrator.config;

import dev.jagt.orchestrator.port.Problem;
import dev.jagt.orchestrator.task.ActionOrigin;
import dev.jagt.orchestrator.task.AgentReport;
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
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Which step may be sent to a session at which status and which replies the turn it opens takes, or which move the
 * Master may make at which status.
 */
public record CaseBook(Shape shape, List<Case> cases, List<Problem> faults) {

    /** What a case names; a move takes no reply, as the session's case of its step holds that. */
    public enum Shape {
        STEPS("step", "which no brief section holds", "no case sends the brief step '%s' — add one, or it never reaches a session"),
        MOVES("move", "which is no verb the Master presses", "no case names the Master's move '%s' — add one, or the Master never makes it");

        private final String key;
        private final String unknown;
        private final String unnamed;

        Shape(String key, String unknown, String unnamed) {
            this.key = key;
            this.unknown = unknown;
            this.unnamed = unnamed;
        }
    }

    public enum By {
        JAGT, MASTER, HUMAN;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        static Optional<By> of(String id) {
            return Arrays.stream(values()).filter(by -> by.id().equals(id)).findFirst();
        }

        /** A call over MCP is a session's own, so what it sets off is jagt's answer to it. */
        public static By of(@Nullable ActionOrigin origin) {
            if (origin == null) {
                return JAGT;
            }
            return switch (origin) {
                case BOARD, PALETTE -> HUMAN;
                case MASTER -> MASTER;
                case MCP, AUTO_REVIEW, TRACKER -> JAGT;
            };
        }
    }

    public record Case(String file, String id, String step, Set<TaskStatus> at, By by, Replies replies) {
    }

    /** A reply is a reported status and what the report means; both must be named. */
    public record Replies(Set<TaskStatus> statuses, Set<AgentReport> kinds) {

        static final Replies NONE = new Replies(Set.of(), Set.of());

        public Replies {
            statuses = Set.copyOf(statuses);
            kinds = Set.copyOf(kinds);
        }

        public boolean takes(TaskStatus status, AgentReport kind) {
            return statuses.contains(status) && kinds.contains(kind);
        }

        Replies plus(Replies other) {
            return new Replies(union(statuses, other.statuses), union(kinds, other.kinds));
        }

        @Override
        public String toString() {
            return String.join(", ", new TreeSet<>(statuses.stream().map(Enum::name).toList())) + "; "
                    + String.join(", ", new TreeSet<>(kinds.stream().map(Replies::kind).toList()));
        }

        public static String kind(AgentReport report) {
            return report.name().toLowerCase(Locale.ROOT);
        }

        private static <T> Set<T> union(Set<T> one, Set<T> two) {
            return Stream.concat(one.stream(), two.stream()).collect(Collectors.toSet());
        }
    }

    private static final Pattern KEY = Pattern.compile("(step|move|at|by|replies):\\s*(.*)");
    private static final String ANY = "any";
    private static final String ANY_BUT = "any but ";

    public CaseBook {
        cases = List.copyOf(cases);
        faults = List.copyOf(faults);
    }

    public static CaseBook empty(Shape shape) {
        return new CaseBook(shape, List.of(), List.of());
    }

    public static CaseBook parse(String file, String text) {
        return parse(Shape.STEPS, file, text);
    }

    public static CaseBook parseMoves(String file, String text) {
        return parse(Shape.MOVES, file, text);
    }

    private static CaseBook parse(Shape shape, String file, String text) {
        List<Case> cases = new ArrayList<>();
        List<Problem> faults = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Sections.Section section : Sections.of(text)) {
            if (!seen.add(section.id())) {
                faults.add(fault(file, section.id(), "is written twice"));
                continue;
            }
            read(shape, file, section, faults).ifPresent(cases::add);
        }
        return new CaseBook(shape, cases, faults);
    }

    /** This book's cases replace the shipped one's of the same id; the rest of the shipped book stands. */
    public CaseBook over(CaseBook shipped) {
        Map<String, Case> merged = new LinkedHashMap<>();
        shipped.cases.forEach(c -> merged.put(c.id(), c));
        cases.forEach(c -> merged.put(c.id(), c));
        return new CaseBook(shape, List.copyOf(merged.values()),
                Stream.concat(faults.stream(), shipped.faults.stream()).toList());
    }

    public CaseBook failing(List<Problem> more) {
        return new CaseBook(shape, cases, Stream.concat(faults.stream(), more.stream()).toList());
    }

    /** Whoever sets the step off. */
    public boolean sends(String step, TaskStatus status) {
        return cases.stream().anyMatch(c -> c.step().equals(step) && c.at().contains(status));
    }

    public boolean sends(String step, TaskStatus status, By by) {
        return cases.stream().anyMatch(c -> c.step().equals(step) && c.at().contains(status) && c.by() == by);
    }

    /** The steps of {@code steps} no case set off {@code by} names. */
    public Set<String> unnamed(Set<String> steps, By by) {
        return steps.stream().filter(step -> cases.stream().noneMatch(c -> c.step().equals(step) && c.by() == by))
                .collect(Collectors.toCollection(TreeSet::new));
    }

    /** What the cases naming any of the {@code +}-joined steps take together; empty where none names one. */
    public Optional<Replies> replies(String step) {
        List<String> steps = List.of(step.split("\\+"));
        return cases.stream().filter(c -> steps.contains(c.step())).map(Case::replies).reduce(Replies::plus);
    }

    /** What keeps this book from matching {@code named}, both ways, beside what it could not read. */
    public List<Problem> problems(Set<String> named, String file) {
        List<Problem> problems = new ArrayList<>(faults);
        cases.stream().filter(c -> !named.contains(c.step()))
                .forEach(c -> problems.add(fault(c.file(), c.id(), "names " + shape.key + " '" + c.step()
                        + "', " + shape.unknown)));
        named.stream().filter(step -> cases.stream().noneMatch(c -> c.step().equals(step)))
                .forEach(step -> problems.add(new Problem(file, file + ": " + shape.unnamed.formatted(step))));
        return List.copyOf(problems);
    }

    private static Optional<Case> read(Shape shape, String file, Sections.Section section, List<Problem> faults) {
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
        String step = keys.getOrDefault(shape.key, "");
        if (step.isEmpty()) {
            faults.add(fault(file, id, "names no " + shape.key));
        }
        Optional<Set<TaskStatus>> at = statuses(keys.getOrDefault("at", ""));
        if (at.isEmpty()) {
            faults.add(fault(file, id, "has at: '" + keys.getOrDefault("at", "")
                    + "', which is not any, any but <statuses>, or a list of " + Arrays.toString(TaskStatus.values())));
        }
        Optional<By> by = By.of(keys.getOrDefault("by", "")).filter(named -> shape == Shape.STEPS || named == By.MASTER);
        if (by.isEmpty()) {
            faults.add(fault(file, id, "has by: '" + keys.getOrDefault("by", "") + "', which is not "
                    + (shape == Shape.STEPS ? "jagt, master or human" : "master")));
        }
        Optional<Replies> replies = shape == Shape.MOVES ? Optional.of(Replies.NONE)
                : readReplies(keys.getOrDefault("replies", ""), faults, file, id);
        if (says.isEmpty()) {
            faults.add(fault(file, id, "says nothing of what it is"));
        }
        return faults.size() > before ? Optional.empty()
                : Optional.of(new Case(file, id, step, at.orElseThrow(), by.orElseThrow(), replies.orElseThrow()));
    }

    private static Optional<Replies> readReplies(String line, List<Problem> faults, String file, String id) {
        String[] parts = line.split(";", -1);
        Optional<Set<TaskStatus>> statuses = parts.length == 2 ? statuses(parts[0].strip()) : Optional.empty();
        Optional<Set<AgentReport>> kinds = parts.length == 2 ? kinds(parts[1]) : Optional.empty();
        if (statuses.isEmpty() || kinds.isEmpty()) {
            faults.add(fault(file, id, "has replies: '" + line + "', which is not <statuses>; <kinds> of "
                    + Arrays.stream(AgentReport.values()).map(Replies::kind).toList()));
            return Optional.empty();
        }
        return Optional.of(new Replies(statuses.get(), kinds.get()));
    }

    private static Optional<Set<AgentReport>> kinds(String list) {
        Set<AgentReport> kinds = EnumSet.noneOf(AgentReport.class);
        for (String name : list.split(",")) {
            Optional<AgentReport> kind = Arrays.stream(AgentReport.values())
                    .filter(value -> Replies.kind(value).equals(name.strip())).findFirst();
            if (kind.isEmpty()) {
                return Optional.empty();
            }
            kinds.add(kind.get());
        }
        return Optional.of(kinds);
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
