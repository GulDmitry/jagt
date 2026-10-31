package dev.jagt.orchestrator.adapter.assistant;

import dev.jagt.orchestrator.port.EnvironmentProbe;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Component
public class HttpEnvironmentProbe implements EnvironmentProbe {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();

    @Override
    public boolean answers(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(TIMEOUT).GET().build();
            return http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode() < 500;
        } catch (IOException | IllegalArgumentException unreachable) {
            return false;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
