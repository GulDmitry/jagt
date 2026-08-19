package dev.jagt.orchestrator.startup;

import dev.jagt.orchestrator.codehost.CodeHost;
import dev.jagt.orchestrator.config.CodeHostProperties;
import dev.jagt.orchestrator.config.TrackerProperties;
import dev.jagt.orchestrator.tracker.Tracker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * The two outside systems jagt may read for itself. Each is optional and off by default, but a type that IS
 * set must select something usable: a misspelled type, a missing token and a base URL nothing can be matched
 * against all end the same way — every read silently falls back to a paid model call, which nobody notices
 * until the bill.
 */
@Component
@RequiredArgsConstructor
public class OutsideReadsCheck implements StartupCheck {

    private final CodeHostProperties codeHost;
    private final List<CodeHost> codeHosts;
    private final TrackerProperties tracker;
    private final List<Tracker> trackers;

    @Override
    public List<String> problems() {
        List<String> problems = new ArrayList<>(problems("orchestrator.code-host", codeHost.type(),
                codeHosts.isEmpty(), codeHost.baseUrl(), codeHost.token()));
        problems.addAll(problems("orchestrator.tracker", tracker.type(), trackers.isEmpty(),
                tracker.baseUrl(), tracker.token()));
        return problems;
    }

    private static List<String> problems(String prefix, String type, boolean nothingSelected, String baseUrl,
                                         String token) {
        if (type == null) {
            return List.of();
        }
        if (nothingSelected) {
            return List.of(prefix + ".type=" + type + " selects nothing — no implementation answers to that"
                    + " name. Fix it, or blank it to read through the assistant instead.");
        }
        List<String> problems = new ArrayList<>();
        if (baseUrl == null) {
            problems.add(prefix + ".type=" + type + " needs " + prefix + ".base-url: a reference is only read"
                    + " under that prefix, so nothing would ever be claimed.");
        } else if (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://")) {
            problems.add(prefix + ".base-url '" + baseUrl + "' is not an http(s) URL.");
        }
        if (token == null) {
            problems.add(prefix + ".type=" + type + " needs " + prefix + ".token.");
        }
        return problems;
    }
}
