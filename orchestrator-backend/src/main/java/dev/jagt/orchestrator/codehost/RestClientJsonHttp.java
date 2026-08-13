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

/** {@link JsonHttp} over Spring's {@code RestClient}. Read-only by construction: it can only GET. */
@Component
public class RestClientJsonHttp implements JsonHttp {

    private static final Logger log = LoggerFactory.getLogger(RestClientJsonHttp.class);
    /** A code-host read runs on the auto-review tick, so it must fail fast rather than hold the poll. */
    private static final Duration TIMEOUT = Duration.ofSeconds(20);

    private final RestClient client;

    public RestClientJsonHttp() {
        // Its own client, not the auto-configured builder: this GETs one host's API on a background poll and
        // wants nothing from the app's web stack but a timeout that cannot hang the auto-review tick.
        JdkClientHttpRequestFactory factory =
                new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(TIMEOUT).build());
        factory.setReadTimeout(TIMEOUT);
        this.client = RestClient.builder().requestFactory(factory).build();
    }

    @Override
    public Optional<JsonNode> get(String url, Map<String, String> headers) {
        try {
            // URI, not the String overload: that one is a URI TEMPLATE and would re-encode the %2F-escaped
            // project path the hosts' APIs need.
            JsonNode body = client.get().uri(URI.create(url))
                    .accept(MediaType.APPLICATION_JSON)
                    .headers(h -> headers.forEach(h::set))
                    .retrieve().body(JsonNode.class);
            return Optional.ofNullable(body);
        } catch (RuntimeException e) {
            // Includes 4xx/5xx (RestClient throws by default) and transport failures. The caller degrades to
            // "unreadable", which the human sees — swallowing the cause here would look like an empty review.
            log.warn("Code-host GET {} failed: {}", url, e.toString());
            return Optional.empty();
        }
    }
}
