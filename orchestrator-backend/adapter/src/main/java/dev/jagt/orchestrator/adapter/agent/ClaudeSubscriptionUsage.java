package dev.jagt.orchestrator.adapter.agent;

import dev.jagt.orchestrator.port.SubscriptionUsage;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.Clock;
import java.util.Map;

@Component
@ConditionalOnProperty(name = "orchestrator.agent.cli", havingValue = "claude", matchIfMissing = true)
@RequiredArgsConstructor
public class ClaudeSubscriptionUsage implements SubscriptionUsage {

    static final URI USAGE = URI.create("https://api.anthropic.com/api/oauth/usage");

    private final ClaudeCredentials credentials;
    private final UsageEndpoint endpoint;
    private final Clock clock;

    @Override
    public Answer read() {
        return UsageRead.of(credentials.read(clock.instant()), endpoint, USAGE, "claude",
                token -> Map.of("Authorization", "Bearer " + token.accessToken(), "anthropic-beta", "oauth-2025-04-20"),
                ClaudeUsageJson::parse);
    }
}
