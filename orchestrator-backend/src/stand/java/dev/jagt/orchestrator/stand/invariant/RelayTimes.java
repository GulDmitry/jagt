package dev.jagt.orchestrator.stand.invariant;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class RelayTimes {

    private static final Duration SAMPLED_TO_THE_SECOND = Duration.ofSeconds(1);

    private RelayTimes() {
    }

    static List<RunRecord.Relay> resolve(List<RunRecord.Relay> sampled, Map<String, List<Instant>> logged) {
        Map<String, Instant> previous = new HashMap<>();
        List<RunRecord.Relay> resolved = new ArrayList<>();
        for (RunRecord.Relay relay : sampled) {
            Instant after = previous.getOrDefault(relay.task(), Instant.MIN);
            Instant until = relay.at().plus(SAMPLED_TO_THE_SECOND);
            Instant at = logged.getOrDefault(relay.task(), List.of()).stream()
                    .filter(time -> time.isAfter(after) && !time.isAfter(until))
                    .reduce((first, second) -> second).orElse(relay.at());
            previous.put(relay.task(), until);
            resolved.add(new RunRecord.Relay(at, relay.task(), relay.steps(),
                    relay.line() + (at.equals(relay.at()) ? "" : " (relayed at " + at + ")")));
        }
        return List.copyOf(resolved);
    }
}
