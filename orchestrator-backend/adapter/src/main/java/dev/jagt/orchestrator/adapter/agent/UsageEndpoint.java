package dev.jagt.orchestrator.adapter.agent;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** One GET of a vendor's usage endpoint; a 429 holds every later call back for as long as it asked. */
@Component
public class UsageEndpoint {

    sealed interface Reply {
    }

    record Body(String json) implements Reply {
    }

    record Refused(int status, String cause) implements Reply {
    }

    static final int NOT_SENT = 0;
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final Duration DEFAULT_COOLDOWN = Duration.ofMinutes(5);

    private final HttpClient http;
    private final Clock clock;
    private final Map<URI, Instant> coolingUntil = new ConcurrentHashMap<>();

    public UsageEndpoint(HttpClient http, Clock clock) {
        this.http = http;
        this.clock = clock;
    }

    Reply get(URI uri, Map<String, String> headers) {
        Instant until = coolingUntil.get(uri);
        if (until != null && clock.instant().isBefore(until)) {
            return new Refused(NOT_SENT, "rate limited until " + until);
        }
        HttpRequest.Builder request = HttpRequest.newBuilder(uri).timeout(TIMEOUT).GET();
        headers.forEach(request::header);
        try {
            HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 429) {
                Instant next = clock.instant().plus(retryAfter(response));
                coolingUntil.put(uri, next);
                return new Refused(429, "rate limited until " + next);
            }
            if (response.statusCode() / 100 != 2) {
                return new Refused(response.statusCode(), "http " + response.statusCode());
            }
            return new Body(response.body());
        } catch (IOException e) {
            return new Refused(NOT_SENT, e.getClass().getSimpleName() + " " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Refused(NOT_SENT, "interrupted");
        }
    }

    private static Duration retryAfter(HttpResponse<String> response) {
        return response.headers().firstValue("Retry-After")
                .filter(value -> value.strip().matches("\\d+"))
                .map(value -> Duration.ofSeconds(Long.parseLong(value.strip())))
                .orElse(DEFAULT_COOLDOWN);
    }
}
