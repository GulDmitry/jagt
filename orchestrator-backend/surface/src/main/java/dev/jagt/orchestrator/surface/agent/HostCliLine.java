package dev.jagt.orchestrator.surface.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A code host's CLI in command position runs only as a read; a quoted span is one word of data. */
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
    /** A field or a body makes the host's API call a POST. */
    private static final Pattern HOST_BODY = Pattern.compile("-[fF].*|--(field|raw-field|input)(=.*)?");
    private static final Pattern HOST_METHOD = Pattern.compile("(-X|--method=?)(.*)");
    private static final Pattern ASSIGNMENT = Pattern.compile("[A-Za-z_]\\w*=.*");
    private static final Set<String> WRAPPERS = Set.of("env", "command", "exec", "xargs");
    private static final Set<String> WRAPPER_OPTION_WITH_VALUE =
            Set.of("-u", "-C", "-S", "-a", "-n", "-I", "-L", "-P", "-s", "-d", "-E");

    private HostCliLine() {
    }

    static boolean writes(String segment) {
        List<String> words = shellWords(segment);
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
        return at < words.size() && HOST_CLI.matcher(words.get(at)).matches() && !reads(words, at + 1);
    }

    private static boolean reads(List<String> words, int from) {
        String command = from < words.size() ? words.get(from) : "";
        if (!"api".equals(command)) {
            return from + 1 < words.size() && HOST_READS.getOrDefault(command, Set.of()).contains(words.get(from + 1));
        }
        for (int at = from + 1; at < words.size(); at++) {
            Matcher method = HOST_METHOD.matcher(words.get(at));
            String named = !method.matches() ? "GET"
                    : method.group(2).isEmpty() && at + 1 < words.size() ? words.get(at + 1) : method.group(2);
            if (HOST_BODY.matcher(words.get(at)).matches() || !"GET".equalsIgnoreCase(named)) {
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
