package dev.jagt.orchestrator.adapter.assistant;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * What a read on OpenCode may touch, translated from the Claude-syntax permission lists a role declares:
 * {@code Bash(git log:*)} becomes the glob {@code git log*}, {@code Read(<glob>)} a path no read may open.
 */
record OpenCodeScope(List<String> bashAllowed, List<String> bashRefused, List<String> readRefused, boolean fetch,
                     List<Path> roots, List<String> environments) {

    static OpenCodeScope of(List<String> allow, List<String> refuse, boolean fetch, List<Path> roots,
                            List<String> environments) {
        return new OpenCodeScope(inside("Bash", allow).map(OpenCodeScope::glob).toList(),
                inside("Bash", refuse).map(OpenCodeScope::glob).toList(), inside("Read", refuse).toList(), fetch, roots,
                environments);
    }

    private static Stream<String> inside(String tool, List<String> entries) {
        return entries.stream().filter(entry -> entry.startsWith(tool + "(") && entry.endsWith(")"))
                .map(entry -> entry.substring(tool.length() + 1, entry.length() - 1));
    }

    private static String glob(String pattern) {
        return pattern.endsWith(":*") ? pattern.substring(0, pattern.length() - 2) + "*" : pattern;
    }
}
