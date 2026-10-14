package dev.jagt.orchestrator.service.master;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * A finding line, {@code - [role] file — issue (pattern)}, matched on its file and the words of its pattern, else of
 * its issue: a reviewer rewords an issue every round but keeps its pattern's name.
 */
final class FindingMatch {

    /** Jaccard of the two word sets at or past which the same file's finding is the same finding. */
    private static final double SAME = 0.5;

    private static final Pattern LINE = Pattern.compile("^- \\[[^]]*] (.+?) — (.*?)(?: \\(([^()]*)\\))?$");

    private FindingMatch() {
    }

    private record Key(String file, Set<String> words) {

        boolean same(Key other) {
            if (!file.equals(other.file) || words.isEmpty()) {
                return false;
            }
            Set<String> both = new HashSet<>(words);
            both.retainAll(other.words);
            Set<String> either = new HashSet<>(words);
            either.addAll(other.words);
            return (double) both.size() / either.size() >= SAME;
        }
    }

    static boolean repeats(String finding, List<String> earlier) {
        return key(finding).map(now -> earlier.stream().map(FindingMatch::key).flatMap(Optional::stream)
                .anyMatch(now::same)).orElse(false);
    }

    private static Optional<Key> key(String line) {
        Matcher parts = LINE.matcher(line.strip());
        if (!parts.matches()) {
            return Optional.empty();
        }
        String title = parts.group(3) == null ? parts.group(2) : parts.group(3);
        return Optional.of(new Key(parts.group(1), Arrays.stream(title.toLowerCase(Locale.ROOT).split("[^a-z0-9]+"))
                .filter(word -> word.length() > 2).collect(Collectors.toSet())));
    }
}
