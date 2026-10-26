package dev.jagt.orchestrator.protocol;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A session's verdict on each switch that is on, as {@code yes — <why>} or {@code no — <why>}. */
public final class SwitchJudgements {

    public static final String FIELD = "switches";
    public static final List<String> NAMES = List.of("autoplan", "visualize", "diagnose");
    public static final String DESCRIBES = "Your judgement on each switch the task brief says is on, given on the"
            + " first report: {\"<switch>\": \"yes — <why>\" or \"no — <why>\"}, a why for both.";

    private static final Pattern ANSWER = Pattern.compile("(yes|no) *(?:—|–|-|:) *(\\S.*)", Pattern.CASE_INSENSITIVE);

    private SwitchJudgements() {
    }

    public static List<Violation> violations(Map<String, String> given, List<String> owed) {
        List<Violation> found = new ArrayList<>();
        given.forEach((name, answer) -> {
            if (!NAMES.contains(key(name))) {
                found.add(new Violation(FIELD, "no switch '" + name + "'; the switches are " + NAMES));
            } else if (!ANSWER.matcher(answer.strip()).matches()) {
                found.add(new Violation(FIELD, name + " answers `yes — <why>` or `no — <why>`, not '" + answer + "'"));
            }
        });
        owed.stream().filter(name -> given.keySet().stream().noneMatch(key -> key(key).equals(name)))
                .forEach(name -> found.add(new Violation(FIELD, "answer " + name + " with `yes — <why>` or"
                        + " `no — <why>`: it is on, and the first report carries your judgement of it")));
        return List.copyOf(found);
    }

    /** The well-formed answers of known switches, spelled one way. */
    public static Map<String, String> normalised(Map<String, String> given) {
        Map<String, String> judged = new LinkedHashMap<>();
        given.forEach((name, answer) -> {
            Matcher match = ANSWER.matcher(answer.strip());
            if (NAMES.contains(key(name)) && match.matches()) {
                judged.put(key(name), match.group(1).toLowerCase(Locale.ROOT) + " — " + match.group(2).strip());
            }
        });
        return Map.copyOf(judged);
    }

    private static String key(String name) {
        return name.strip().toLowerCase(Locale.ROOT);
    }
}
