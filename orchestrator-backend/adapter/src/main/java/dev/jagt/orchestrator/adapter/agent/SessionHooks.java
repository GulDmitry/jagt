package dev.jagt.orchestrator.adapter.agent;

import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Arrays;
import java.util.Map;
import java.util.Properties;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Which of one CLI's own events mean what to jagt, as a resource per runtime: this is vendor knowledge, and it
 * changes when the CLI does. A runtime with no resource of its own maps nothing.
 */
final class SessionHooks {

    private SessionHooks() {
    }

    /** Declarations that are not an event-to-state line: one is answered, the other is a vendor's word. */
    private static final String GATE = "gate";
    private static final String COMPACTED_START = "compacted-start";
    private static final String BLOCKING_NOTIFICATION = "blocking-notification";
    private static final String TURN_END = "turn-end";
    private static final String PROMPT_GATE = "prompt-gate";
    private static final String TURN_FAILURE = "turn-failure";
    private static final String RATE_LIMIT_ERROR = "rate-limit-error";
    private static final String CONTINUING_END = "continuing-end";
    private static final String TRUST_DIALOG = "trust-dialog";
    private static final Set<String> NOT_A_STATE =
            Set.of(GATE, PROMPT_GATE, COMPACTED_START, BLOCKING_NOTIFICATION, TURN_END, TURN_FAILURE, RATE_LIMIT_ERROR,
                    CONTINUING_END, TRUST_DIALOG);

    /** The event this CLI answers with allow or deny, or nothing where it has none. */
    static Optional<String> gate(String runtime) {
        return Optional.ofNullable(declared(runtime).getProperty(GATE));
    }

    /** The event ending every turn, answered with whether the turn may end, or nothing where it has none. */
    static Optional<String> turnEnd(String runtime) {
        return Optional.ofNullable(declared(runtime).getProperty(TURN_END));
    }

    /** The event firing as a human submits a prompt, answered with whether it may reach the model; none where absent. */
    static Optional<String> promptGate(String runtime) {
        return Optional.ofNullable(declared(runtime).getProperty(PROMPT_GATE));
    }

    /** The event ending a turn on an API error, or nothing where this CLI has none. */
    static Optional<String> turnFailure(String runtime) {
        return Optional.ofNullable(declared(runtime).getProperty(TURN_FAILURE));
    }

    /** The error word that means a usage limit, or blank where this CLI says nothing. */
    static String rateLimitError(String runtime) {
        return declared(runtime).getProperty(RATE_LIMIT_ERROR, "");
    }

    /** The reasons a session ends that a new start follows at once, or none. */
    static Set<String> continuingEnds(String runtime) {
        return Arrays.stream(declared(runtime).getProperty(CONTINUING_END, "").split(","))
                .map(String::strip).filter(reason -> !reason.isEmpty()).collect(Collectors.toUnmodifiableSet());
    }

    /** Phrases only this CLI's trust dialog shows, any one meaning a session waits on it; none where undeclared. */
    static Set<String> trustDialog(String runtime) {
        return Arrays.stream(declared(runtime).getProperty(TRUST_DIALOG, "").split("\\|"))
                .map(String::strip).filter(phrase -> !phrase.isEmpty()).collect(Collectors.toUnmodifiableSet());
    }

    /** What this CLI calls a start that follows a compaction, or blank where it says nothing about starts. */
    static String compactedStart(String runtime) {
        return declared(runtime).getProperty(COMPACTED_START, "");
    }

    /** What this CLI says in a notification a session cannot go on past, or blank where it says nothing. */
    static String blockingNotification(String runtime) {
        return declared(runtime).getProperty(BLOCKING_NOTIFICATION, "");
    }

    /** Event to state, in a fixed order so the generated file does not churn between provisionings. */
    static Map<String, String> of(String runtime) {
        Properties declared = declared(runtime);
        Map<String, String> events = new LinkedHashMap<>();
        new TreeSet<>(declared.stringPropertyNames()).stream().filter(event -> !NOT_A_STATE.contains(event))
                .forEach(event -> events.put(event, declared.getProperty(event)));
        return events;
    }

    private static Properties declared(String runtime) {
        Properties declared = new Properties();
        try (InputStream in = new ClassPathResource("hooks/" + runtime + ".properties").getInputStream()) {
            declared.load(in);
        } catch (IOException e) {
            return new Properties();
        }
        return declared;
    }
}
