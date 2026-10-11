package dev.jagt.orchestrator.adapter.agent;

import dev.jagt.orchestrator.port.SubscriptionUsage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.Instant;
import java.util.Map;

@Component
@ConditionalOnProperty(name = "orchestrator.agent.cli", havingValue = "codex")
@RequiredArgsConstructor
@Slf4j
public class CodexSubscriptionUsage implements SubscriptionUsage {

    static final URI USAGE = URI.create("https://chatgpt.com/backend-api/wham/usage");

    private final CodexCredentials credentials;
    private final UsageEndpoint endpoint;

    @Override
    public Answer read() {
        Answer answer = switch (credentials.read(Instant.now())) {
            case OAuthCredential.None none -> new NoSubscription();
            case OAuthCredential.Failed failed -> new Unreadable(failed.cause());
            case OAuthCredential.Token token -> switch (endpoint.get(USAGE, Map.of(
                    "Authorization", "Bearer " + token.accessToken(), "ChatGPT-Account-Id", token.accountId()))) {
                case UsageEndpoint.Body body -> CodexUsageJson.parse(body.json());
                case UsageEndpoint.Refused refused when refused.status() == 401 || refused.status() == 403 ->
                        new Unreadable(refused.cause() + ", running codex refreshes the token");
                case UsageEndpoint.Refused refused -> new Unreadable(refused.cause());
            };
        };
        if (answer instanceof Unreadable(String cause)) {
            log.atError().setMessage("usage read failed")
                    .addKeyValue("url", USAGE)
                    .addKeyValue("cause", cause)
                    .log();
        }
        return answer;
    }
}
