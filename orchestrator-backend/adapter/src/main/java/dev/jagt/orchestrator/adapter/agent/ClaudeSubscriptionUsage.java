package dev.jagt.orchestrator.adapter.agent;

import dev.jagt.orchestrator.port.SubscriptionUsage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.Clock;
import java.util.Map;

@Component
@ConditionalOnProperty(name = "orchestrator.agent.cli", havingValue = "claude", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class ClaudeSubscriptionUsage implements SubscriptionUsage {

    static final URI USAGE = URI.create("https://api.anthropic.com/api/oauth/usage");

    private final ClaudeCredentials credentials;
    private final UsageEndpoint endpoint;
    private final Clock clock;

    @Override
    public Answer read() {
        Answer answer = switch (credentials.read(clock.instant())) {
            case OAuthCredential.None none -> new NoSubscription();
            case OAuthCredential.Failed failed -> new Unreadable(failed.cause());
            case OAuthCredential.Token token -> switch (endpoint.get(USAGE, Map.of(
                    "Authorization", "Bearer " + token.accessToken(), "anthropic-beta", "oauth-2025-04-20"))) {
                case UsageEndpoint.Body body -> ClaudeUsageJson.parse(body.json());
                case UsageEndpoint.Refused refused when refused.status() == 401 || refused.status() == 403 ->
                        new Unreadable(refused.cause() + ", running claude refreshes the token");
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
