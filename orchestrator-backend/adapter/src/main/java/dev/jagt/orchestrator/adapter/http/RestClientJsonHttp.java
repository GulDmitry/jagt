package dev.jagt.orchestrator.adapter.http;

import dev.jagt.orchestrator.port.JsonHttp;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

@Component
@Slf4j
public class RestClientJsonHttp implements JsonHttp {

    /** Short, because a call must not hold the background poll it runs on. */
    private static final Duration TIMEOUT = Duration.ofSeconds(20);

    private final RestClient client;

    public RestClientJsonHttp() {
        JdkClientHttpRequestFactory factory =
                new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(TIMEOUT).build());
        factory.setReadTimeout(TIMEOUT);
        this.client = RestClient.builder().requestFactory(factory).build();
    }

    @Override
    public Optional<JsonNode> get(String url, Map<String, String> headers) {
        return send(url, headers, null, false);
    }

    @Override
    public Optional<JsonNode> post(String url, Map<String, String> headers, Map<String, Object> body) {
        return send(url, headers, body, false);
    }

    @Override
    public Optional<JsonNode> put(String url, Map<String, String> headers, Map<String, Object> body) {
        return send(url, headers, body, true);
    }

    private Optional<JsonNode> send(String url, Map<String, String> headers, Map<String, Object> body,
                                    boolean update) {
        try {
            // URI, not the String overload: that one is a URI TEMPLATE and would re-encode the %2F-escaped
            // project path the hosts' APIs need.
            RestClient.RequestHeadersSpec<?> request = body == null
                    ? client.get().uri(URI.create(url))
                    : (update ? client.put() : client.post()).uri(URI.create(url))
                            .contentType(MediaType.APPLICATION_JSON).body(body);
            JsonNode answer = request
                    .accept(MediaType.APPLICATION_JSON)
                    .headers(h -> headers.forEach(h::set))
                    .retrieve().body(JsonNode.class);
            return Optional.ofNullable(answer);
        } catch (RuntimeException e) {
            // Includes 4xx/5xx (RestClient throws by default) as well as transport failures.
            log.warn("{} {} failed: {}", body == null ? "GET" : update ? "PUT" : "POST", url,
                    e.toString());
            return Optional.empty();
        }
    }
}
