package dev.jagt.orchestrator.surface.agent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** A code host's CLI runs only as a read, however it is placed; a quoted span is data unless a shell runs it. */
final class HostCliLine {

    private static final Pattern HOST_CLI = Pattern.compile("(\\S*/)?(gh|glab)");
    private static final Map<String, Set<String>> HOST_READS = Map.of(
            "pr", Set.of("view", "list", "diff", "checks", "status"),
            "mr", Set.of("view", "list", "diff"),
            "issue", Set.of("view", "list", "status"),
            "run", Set.of("view", "list"),
            "ci", Set.of("list", "status"),
            "repo", Set.of("view"),
            "auth", Set.of("status"));
    private static final Pattern HOST_METHOD = Pattern.compile("(-X|--method=?)(.*)");
    /** The only options an {@code api} read may carry; anything else, a body or a bundle, makes it a write. */
    private static final Set<String> API_READ_FLAGS = Set.of("--paginate", "--slurp", "-i", "--include", "--silent",
            "--verbose", "-q", "--jq", "-t", "--template", "-H", "--header", "--hostname", "--cache");
    private static final Pattern METHOD_OVERRIDE = Pattern.compile("(?i)\\s*x-http-method");
    private static final Set<String> API_READ_VALUED = Set.of("-q", "--jq", "-t", "--template", "-H", "--header",
            "--hostname", "--cache");
    private static final Pattern ASSIGNMENT = Pattern.compile("[A-Za-z_]\\w*=.*");
    private static final Set<String> WRAPPERS = Set.of("env", "command", "exec", "xargs");
    private static final Set<String> WRAPPER_OPTION_WITH_VALUE =
            Set.of("-u", "-C", "-S", "-a", "-n", "-I", "-L", "-P", "-s", "-d", "-E");

    /** A host CLI sitting after any word still runs where the next word is one of its own command groups. */
    private static final Set<String> GROUPS = Set.of("agent-task", "alias", "api", "artifact-registry",
            "attestation", "auth", "browse", "cache", "changelog", "check-update", "ci", "cluster", "co", "codespace",
            "completion", "config", "container-registry", "copilot", "dependency-firewall", "deploy-key",
            "discussion", "duo", "extension", "gist", "govern", "gpg-key", "incident", "issue", "iteration", "job",
            "label", "licenses", "mcp", "milestone", "mr", "opentofu", "orbit", "org", "packages", "pr", "preview",
            "project", "release", "repo", "ruleset", "run", "runner", "runner-controller", "schedule", "search",
            "secret", "securefile", "security", "skill", "skills", "snippet", "ssh-key", "stack", "status", "todo",
            "token", "user", "variable", "work-items", "workflow", "-R", "--repo", "--hostname");
    private static final Set<String> REPO_FLAGS = Set.of("-R", "--repo", "--hostname");
    private static final Pattern GLUED_REPO = Pattern.compile("-R.+|--(repo|hostname)=.+");
    private static final Set<String> SHELLS = Set.of("sh", "bash", "zsh", "dash", "ksh", "csh", "tcsh", "fish");

    private HostCliLine() {
    }

    /** What a host CLI may run, as the refusal names it. */
    static String readsAllowed() {
        return new TreeMap<>(HOST_READS).entrySet().stream()
                .map(read -> read.getKey() + " " + String.join("/", new TreeSet<>(read.getValue())))
                .collect(Collectors.joining(", ", "", ", api GET"));
    }

    static boolean writes(String segment) {
        List<String> words = shellWords(segment);
        int command = commandPosition(words);
        for (int at = 0; at < words.size(); at++) {
            String word = words.get(at);
            String next = at + 1 < words.size() ? words.get(at + 1) : "";
            boolean glued = GLUED_REPO.matcher(next).matches();
            boolean called = HOST_CLI.matcher(word).matches()
                    && (at == command || GROUPS.contains(next) || glued || next.contains("{"));
            int from = REPO_FLAGS.contains(next) ? at + 3 : glued ? at + 2 : at + 1;
            if (called && !reads(words, from) || runsLines(words, at)) {
                return true;
            }
        }
        return false;
    }

    private static int commandPosition(List<String> words) {
        int at = 0;
        while (at < words.size()) {
            if (ASSIGNMENT.matcher(words.get(at)).matches()) {
                at++;
            } else if (WRAPPERS.contains(words.get(at).replaceFirst(".*/", ""))) {
                at++;
                while (at < words.size() && words.get(at).startsWith("-")) {
                    at += WRAPPER_OPTION_WITH_VALUE.contains(words.get(at)) ? 2 : 1;
                }
            } else {
                break;
            }
        }
        return at;
    }

    /**
     * A shell, {@code eval} and {@code env -S} may run a later quoted span as a line, so each one holding a space is
     * judged as one; a bare word is already judged where it stands.
     */
    private static boolean runsLines(List<String> words, int at) {
        String word = words.get(at).replaceFirst(".*/", "");
        return (SHELLS.contains(word) || "eval".equals(word) || "env".equals(word))
                && words.subList(at + 1, words.size()).stream()
                .map(later -> later.replaceFirst("^(-S|--split-string=)", ""))
                .filter(later -> later.chars().anyMatch(Character::isWhitespace))
                .anyMatch(HostCliLine::writesLine);
    }

    private static boolean writesLine(String line) {
        return Arrays.stream(line.split("[;&|\\n]+")).anyMatch(HostCliLine::writes);
    }

    private static boolean reads(List<String> words, int from) {
        String command = from < words.size() ? words.get(from) : "";
        if (!"api".equals(command)) {
            return from + 1 < words.size() && HOST_READS.getOrDefault(command, Set.of()).contains(words.get(from + 1));
        }
        for (int at = from + 1; at < words.size(); at++) {
            String word = words.get(at);
            if (!word.startsWith("-")) {
                continue;
            }
            Matcher method = HOST_METHOD.matcher(word);
            if (method.matches()) {
                String named = method.group(2).isEmpty() && at + 1 < words.size() ? words.get(++at) : method.group(2);
                if (!"GET".equalsIgnoreCase(named)) {
                    return false;
                }
            } else if (API_READ_VALUED.contains(word)) {
                if (++at < words.size() && METHOD_OVERRIDE.matcher(words.get(at)).lookingAt()) {
                    return false;
                }
            } else if (!API_READ_FLAGS.contains(word.replaceFirst("=.*", ""))) {
                return false;
            }
        }
        return true;
    }

    /** An unclosed quote keeps its mark, so what follows it never reads as a bare word. */
    private static List<String> shellWords(String segment) {
        List<String> words = new ArrayList<>();
        StringBuilder word = new StringBuilder();
        char quote = 0;
        boolean inWord = false;
        for (char c : segment.toCharArray()) {
            if (quote != 0) {
                if (c == quote) {
                    quote = 0;
                } else {
                    word.append(c);
                }
            } else if (c == '\'' || c == '"') {
                quote = c;
                inWord = true;
            } else if (Character.isWhitespace(c)) {
                if (inWord) {
                    words.add(word.toString());
                    word.setLength(0);
                    inWord = false;
                }
            } else {
                word.append(c);
                inWord = true;
            }
        }
        if (quote != 0) {
            word.insert(0, quote);
        }
        if (inWord) {
            words.add(word.toString());
        }
        return words;
    }
}
