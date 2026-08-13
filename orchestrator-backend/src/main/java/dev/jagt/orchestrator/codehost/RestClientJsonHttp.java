package dev.jagt.orchestrator.codehost;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

/** {@link JsonHttp} over Spring's {@code RestClient}. */
@Component
public class RestClientJsonHttp implements JsonHttp {

    private static final Logger log = LoggerFactory.getLogger(RestClientJsonHttp.class);
    /** A code-host call runs on the auto-review tick or inside `ship`, so it must fail fast, not hold either. */
    private static final Duration TIMEOUT = Duration.ofSeconds(20);

    private final RestClient client;

    public RestClientJsonHttp() {
        // Its own client, not the auto-configured builder: this talks to one host's API on a background poll
        // and wants nothing from the app's web stack but a timeout that cannot hang the auto-review tick.
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
            // Includes 4xx/5xx (RestClient throws by default) and transport failures. Callers degrade to
            // "unreadable" / "not created", which the human sees — swallowing the cause would look like an
            // empty review or a silently missing merge request.
            log.warn("Code-host {} {} failed: {}", body == null ? "GET" : update ? "PUT" : "POST", url,
                    e.toString());
            return Optional.empty();
        }
    }
}
